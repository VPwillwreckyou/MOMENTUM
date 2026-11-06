$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { throw 'C# compiler not found.' }

$source = Join-Path $PSScriptRoot 'Program.cs'
$icon = Join-Path $repo 'apps\desktop\build\icon.ico'
$output = Join-Path $repo 'MOMENTUM.exe'

& $csc /nologo /target:winexe /platform:anycpu /optimize+ /r:System.dll /r:System.Windows.Forms.dll `
    "/win32icon:$icon" "/out:$output" $source
if ($LASTEXITCODE -ne 0) { throw 'MOMENTUM.exe build failed.' }
Write-Host "Built $output"
