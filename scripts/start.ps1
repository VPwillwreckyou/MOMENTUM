param(
    [switch]$Rebuild,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repo
try {
    if (Test-Path (Join-Path $repo '.env')) {
        Write-Host 'Loading .env'
        foreach ($line in Get-Content (Join-Path $repo '.env')) {
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

    $dist = Join-Path $repo 'apps\web\dist\index.html'
    if ($Rebuild -or -not (Test-Path $dist)) {
        Write-Host 'Building MOMENTUM web client...'
        $env:npm_config_cache = Join-Path $repo '.npm-cache'
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' }
    }

    $jar = Join-Path $repo 'services\api\build\momentum-api.jar'
    $sqliteJar = Join-Path $repo 'services\api\lib\sqlite-jdbc.jar'
    $slf4jJar = Join-Path $repo 'services\api\lib\slf4j-api.jar'
    if ($Rebuild -or -not (Test-Path $jar)) {
        Write-Host 'Building MOMENTUM Java service...'
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot '..\services\api\scripts\build.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'API build failed.' }
    }

    $port = if ($env:MOMENTUM_PORT) { $env:MOMENTUM_PORT } else { '8787' }
    $hostName = if ($env:MOMENTUM_HOST -and $env:MOMENTUM_HOST -ne '0.0.0.0') { $env:MOMENTUM_HOST } else { '127.0.0.1' }
    $url = "http://${hostName}:${port}"

    if (-not $NoBrowser) {
        Start-Job -ScriptBlock {
            param($target)
            Start-Sleep -Seconds 3
            Start-Process $target
        } -ArgumentList $url | Out-Null
    }

    Write-Host ''
    Write-Host "MOMENTUM starting at $url"
    Write-Host 'Press Ctrl+C to stop.'
    Write-Host ''
    $cp = $jar
    if (Test-Path $sqliteJar) { $cp = "$cp;$sqliteJar" }
    if (Test-Path $slf4jJar) { $cp = "$cp;$slf4jJar" }
    & java -cp $cp dev.momentum.MomentumServer
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
