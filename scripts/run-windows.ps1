$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$exe = Join-Path $repo 'apps\desktop\release\win-unpacked\MOMENTUM.exe'

if (-not (Test-Path $exe)) {
    Write-Host 'MOMENTUM.exe is not built yet. Building it now...'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'build-windows.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Windows build failed.' }
}

Start-Process -FilePath $exe -WorkingDirectory (Split-Path $exe)
