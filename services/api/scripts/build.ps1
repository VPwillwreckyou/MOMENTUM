param(
    [switch]$NoJar
)

$ErrorActionPreference = 'Stop'
$api = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$src = Join-Path $api 'src\main\java'
$build = Join-Path $api 'build'
$classes = Join-Path $build 'classes'
$jar = Join-Path $build 'momentum-api.jar'

New-Item -ItemType Directory -Force -Path $classes | Out-Null
Get-ChildItem -Path $classes -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force
$files = @(Get-ChildItem -Path $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
if ($files.Count -eq 0) { throw 'No Java sources found.' }

Write-Host "Compiling $($files.Count) Java files..."
& javac --release 21 -encoding UTF-8 -Xlint:all -d $classes @files
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

if ($NoJar) {
    Write-Host 'Skipping jar creation (-NoJar).'
    exit 0
}

$manifest = Join-Path $build 'manifest.txt'
@(
    'Manifest-Version: 1.0'
    'Main-Class: dev.momentum.MomentumServer'
    'Implementation-Title: MOMENTUM API'
    'Implementation-Version: 1.0.0'
) | Set-Content -Path $manifest -Encoding ASCII
& jar --create --file $jar --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
Write-Host "Built $jar"
