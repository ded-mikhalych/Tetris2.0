param(
    [string]$ServerHost = "localhost",
    [int]$Port = 1099
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot "out"
$libDir = Join-Path $projectRoot "lib"

if (-not (Test-Path $outputDir)) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

$classPath = $outputDir
if (Test-Path $libDir) {
    $jars = Get-ChildItem -Path $libDir -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    if ($jars.Count -gt 0) {
        $classPath = @($outputDir) + $jars -join ';'
    }
}

java -cp $classPath ru.danil.tetris.rmi.client.TetrisSwingClient $ServerHost $Port
