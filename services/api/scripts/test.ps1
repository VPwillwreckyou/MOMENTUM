$ErrorActionPreference = 'Stop'
$api = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$src = Join-Path $api 'src\test\java'
$build = Join-Path $api 'build'
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'

& (Join-Path $PSScriptRoot 'build.ps1') -NoJar
if ($LASTEXITCODE -ne 0) { throw 'API build failed.' }

New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
Get-ChildItem -Path $testClasses -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force
$files = @(Get-ChildItem -Path $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
if ($files.Count -eq 0) { throw 'No Java tests found.' }

$lib = Join-Path $api 'lib'
$sqliteJar = Join-Path $lib 'sqlite-jdbc.jar'
$slf4jJar = Join-Path $lib 'slf4j-api.jar'
$separator = [IO.Path]::PathSeparator
$cpList = @($classes, $sqliteJar, $slf4jJar) | Where-Object { Test-Path $_ }
$cp = $cpList -join $separator
$baseCp = "$cp$separator$testClasses"

Write-Host "Compiling $($files.Count) test files..."
& javac --release 21 -encoding UTF-8 -cp $cp -d $testClasses @files
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed with exit code $LASTEXITCODE" }

& java -ea -cp $baseCp dev.momentum.TestRunner
exit $LASTEXITCODE
