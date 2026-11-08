param(
    [switch]$NoJar
)

$ErrorActionPreference = 'Stop'
$api = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$src = Join-Path $api 'src\main\java'
$lib = Join-Path $api 'lib'
$build = Join-Path $api 'build'
$classes = Join-Path $build 'classes'
$jar = Join-Path $build 'momentum-api.jar'

# SQLite JDBC + SLF4J
$sqliteJar = Join-Path $lib 'sqlite-jdbc.jar'
$slf4jJar = Join-Path $lib 'slf4j-api.jar'
if (-not (Test-Path $sqliteJar)) {
    New-Item -ItemType Directory -Force -Path $lib | Out-Null
    Write-Host 'Downloading SQLite JDBC...'
    $url = 'https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar'
    Invoke-WebRequest -Uri $url -OutFile $sqliteJar -UseBasicParsing
    if (-not (Test-Path $sqliteJar)) { throw 'Failed to download SQLite JDBC.' }
}
if (-not (Test-Path $slf4jJar)) {
    New-Item -ItemType Directory -Force -Path $lib | Out-Null
    Write-Host 'Downloading SLF4J API...'
    $url = 'https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar'
    Invoke-WebRequest -Uri $url -OutFile $slf4jJar -UseBasicParsing
    if (-not (Test-Path $slf4jJar)) { throw 'Failed to download SLF4J API.' }
}
$cp = "$sqliteJar;$slf4jJar"

New-Item -ItemType Directory -Force -Path $classes | Out-Null
Get-ChildItem -Path $classes -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force
$files = @(Get-ChildItem -Path $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
if ($files.Count -eq 0) { throw 'No Java sources found.' }

Write-Host "Compiling $($files.Count) Java files..."
if ($cp) {
    & javac --release 21 -encoding UTF-8 -cp $cp -Xlint:all -d $classes @files
} else {
    & javac --release 21 -encoding UTF-8 -Xlint:all -d $classes @files
}
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

if ($NoJar) {
    Write-Host 'Skipping jar creation (-NoJar).'
    exit 0
}

$manifest = Join-Path $build 'manifest.txt'
$cpEntry = if ($cp) { "Class-Path: lib/sqlite-jdbc.jar lib/slf4j-api.jar" } else { '' }
@(
    'Manifest-Version: 1.0'
    'Main-Class: dev.momentum.MomentumServer'
    'Implementation-Title: MOMENTUM API'
    'Implementation-Version: 1.0.0'
) + @($cpEntry) | Where-Object { $_ } | Set-Content -Path $manifest -Encoding ASCII
& jar --create --file $jar --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
Write-Host "Built $jar"
