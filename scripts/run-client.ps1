param(
    [string]$ServerHost = "localhost",
    [int]$Port = 1099
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot "out"

if (-not (Test-Path $outputDir)) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

java -cp $outputDir ru.danil.tetris.rmi.client.TetrisSwingClient $ServerHost $Port
