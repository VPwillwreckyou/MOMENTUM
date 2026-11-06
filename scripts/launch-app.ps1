# Launch MOMENTUM as a Windows app window (Edge app mode).
# Starts the local Java service if needed, then opens a standalone window.
# Use -Browser to open the same local site in the default browser instead.
param(
    [switch]$NoWindow,
    [switch]$Browser
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repo
try {
    # ---- Load .env ----
    $envFile = Join-Path $repo '.env'
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            $trimmed = $line.Trim()
            if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
            $parts = $trimmed -split '=', 2
            if ($parts.Count -ne 2) { continue }
            $name = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"').Trim("'")
            if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            }
        }
    }

    # ---- Configure runtime paths ----
    $port    = if ($env:MOMENTUM_PORT) { $env:MOMENTUM_PORT } else { '8787' }
    $hostName = if ($env:MOMENTUM_HOST -and $env:MOMENTUM_HOST -ne '0.0.0.0') { $env:MOMENTUM_HOST } else { '127.0.0.1' }
    $url     = "http://${hostName}:${port}"
    $env:MOMENTUM_PORT        = $port
    $env:MOMENTUM_HOST        = $hostName
    $env:MOMENTUM_PUBLIC_URL  = $url
    $env:MOMENTUM_APP_URL     = $url
    $env:MOMENTUM_COOKIE_SECURE = 'false'
    if (-not $env:MOMENTUM_DATA_DIR  -or $env:MOMENTUM_DATA_DIR -like '*\AppData\*') { $env:MOMENTUM_DATA_DIR = Join-Path $repo 'data' }
    if (-not $env:MOMENTUM_WEB_DIST) { $env:MOMENTUM_WEB_DIST = Join-Path $repo 'apps\web\dist' }

    # ---- Build web client / jar when missing ----
    if (-not (Test-Path (Join-Path $env:MOMENTUM_WEB_DIST 'index.html'))) {
        Write-Host 'Building MOMENTUM web client...'
        $env:npm_config_cache = Join-Path $repo '.npm-cache'
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' }
    }
    $jar = Join-Path $repo 'services\api\build\momentum-api.jar'
    if (-not (Test-Path $jar)) {
        Write-Host 'Building MOMENTUM Java service...'
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot '..\services\api\scripts\build.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'API build failed.' }
    }

    # ---- Start local service if not already running ----
    function Test-MomentumHealth([string]$base) {
        try {
            $r = Invoke-WebRequest -Uri "$base/api/health" -UseBasicParsing -TimeoutSec 3
            return ($r.StatusCode -eq 200)
        } catch { return $false }
    }

    if (-not (Test-MomentumHealth $url)) {
        Write-Host 'Starting MOMENTUM local service (hidden)...'
        $javaExe = $null
        if ($env:JAVA_HOME) {
            $candidate = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
            if (Test-Path $candidate) { $javaExe = $candidate }
        }
        if (-not $javaExe) {
            $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
            if (-not $cmd) { $cmd = Get-Command java.exe -ErrorAction SilentlyContinue }
            if (-not $cmd) { throw 'Java not found. Install a Java runtime to run MOMENTUM.' }
            $javaExe = $cmd.Source -replace 'java\.exe$', 'javaw.exe'
        }

        # Capture the service output so silent crashes are always debuggable.
        $logDir = Join-Path $env:TEMP 'momentum'
        New-Item -ItemType Directory -Force $logDir | Out-Null
        $outLog = Join-Path $logDir 'service-out.log'
        $errLog = Join-Path $logDir 'service-err.log'
        Clear-Content $outLog -ErrorAction SilentlyContinue
        Clear-Content $errLog -ErrorAction SilentlyContinue

        function Start-ServiceProcess {
            # javaw has no console, so redirects work without showing a window.
            $script:serviceProc = Start-Process -FilePath $javaExe -ArgumentList @('-jar', $jar) -WorkingDirectory $repo `
                -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
        }

        $readyAfterStart = $false
        for ($attempt = 1; $attempt -le 2 -and -not $readyAfterStart; $attempt++) {
            Start-ServiceProcess
            for ($i = 0; $i -lt 40; $i++) {
                if (Test-MomentumHealth $url) { $readyAfterStart = $true; break }
                # If the process already died, restart immediately instead of waiting.
                if ($script:serviceProc.HasExited) { break }
                Start-Sleep -Milliseconds 500
            }
            if ($readyAfterStart) {
                # Give the JVM a moment to confirm it survives its startup phase.
                Start-Sleep -Seconds 4
                if ($script:serviceProc.HasExited -or -not (Test-MomentumHealth $url)) {
                    $readyAfterStart = $false
                }
            }
            if (-not $readyAfterStart -and $attempt -lt 2) {
                Write-Host 'Service did not stay up on first start - retrying once...'
            }
        }
        if (-not $readyAfterStart) {
            $detail = ''
            if (Test-Path $errLog) { $detail = Get-Content $errLog -Raw -ErrorAction SilentlyContinue }
            if (-not $detail -and (Test-Path $outLog)) { $detail = Get-Content $outLog -Raw -ErrorAction SilentlyContinue }
            throw "MOMENTUM local service failed to start at $url.$([Environment]::NewLine)$detail$([Environment]::NewLine)Service logs: $outLog / $errLog"
        }
    }

    # ---- Wait for the service ----
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        if (Test-MomentumHealth $url) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "MOMENTUM local service did not become ready at $url." }
    Write-Host "MOMENTUM service ready at $url"

    if ($NoWindow) { return }

    if ($Browser) {
        Start-Process $url
        return
    }

    # ---- Open as a standalone app window (Edge app mode, Chrome fallback, browser fallback) ----
    $candidates = @()
    $candidates += "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    $candidates += "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
    $candidates += (Get-Command msedge.exe -ErrorAction SilentlyContinue).Source
    $candidates += "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe"
    $candidates += "$env:ProgramFiles\Google\Chrome\Application\chrome.exe"
    $candidates += (Get-Command chrome.exe -ErrorAction SilentlyContinue).Source
    $appBrowser = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1

    $opened = $false
    if ($appBrowser) {
        Write-Host "Opening app window with $appBrowser"
        $profileDir = Join-Path $env:LOCALAPPDATA 'MOMENTUMAppProfile'
        try {
            Start-Process -FilePath $appBrowser -ArgumentList @(
                "--app=$url",
                "--user-data-dir=$profileDir",
                '--no-first-run',
                '--no-default-browser-check'
            )
            $opened = $true
        } catch {
            Write-Host "Could not start $appBrowser ($($_.Exception.Message)). Trying default browser."
        }
    }
    if (-not $opened) {
        Start-Process $url
    }
}
finally {
    Pop-Location
}
