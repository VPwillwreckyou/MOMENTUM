param(
    [switch]$Installer
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repo
try {
    if (-not (Test-Path (Join-Path $repo '.env'))) {
        throw 'Missing .env. Create it from .env.example and add GROQ_API_KEY.'
    }

    Write-Host 'Building web client...'
    $env:npm_config_cache = Join-Path $repo '.npm-cache'
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' }

    Write-Host 'Building Java service...'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'services\api\scripts\build.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'API build failed.' }

    Write-Host 'Preparing desktop runtime...'
    Push-Location (Join-Path $repo 'apps\desktop')
    try {
        & npm.cmd run prepare:web
        if ($LASTEXITCODE -ne 0) { throw 'Web copy failed.' }
        & npm.cmd run prepare:runtime
        if ($LASTEXITCODE -ne 0) { throw 'Runtime preparation failed.' }

        if ($Installer) {
            Write-Host 'Packaging Windows installer...'
            & npx.cmd electron-builder --win --x64
        } else {
            Write-Host 'Packaging unpacked Windows application...'
            & npx.cmd electron-builder --win --x64 --dir
        }
        if ($LASTEXITCODE -ne 0) { throw 'Electron packaging failed.' }
    }
    finally {
        Pop-Location
    }

    $exe = Join-Path $repo 'apps\desktop\release\win-unpacked\MOMENTUM.exe'
    if (-not (Test-Path $exe)) { throw 'MOMENTUM.exe was not produced.' }
    Write-Host ''
    Write-Host "Windows app ready: $exe"
}
finally {
    Pop-Location
}
