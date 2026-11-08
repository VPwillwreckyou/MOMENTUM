$ErrorActionPreference = 'Stop'
$api = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $api '..\..')).Path
$jar = Join-Path $api 'build\momentum-api.jar'
$lib = Join-Path $api 'lib'
$sqliteJar = Join-Path $lib 'sqlite-jdbc.jar'

if (-not (Test-Path $jar)) {
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'API build failed.' }
}

if (-not $env:MOMENTUM_WEB_DIST) {
    $env:MOMENTUM_WEB_DIST = Join-Path $repo 'apps\web\dist'
}

$envFile = Join-Path $repo '.env'
if (Test-Path $envFile) {
    Write-Host 'Loading .env'
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

$cp = $jar
if (Test-Path $sqliteJar) { $cp = "$cp;$sqliteJar" }
$slf4jJar = Join-Path $api 'lib\slf4j-api.jar'
if (Test-Path $slf4jJar) { $cp = "$cp;$slf4jJar" }

Push-Location $repo
try {
    Write-Host "Starting MOMENTUM API from $repo"
    & java -cp $cp dev.momentum.MomentumServer
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
