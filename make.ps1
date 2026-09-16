param(
    [Parameter(Position = 0)]
    [string]$Target = "help",

    [string]$Service
)

$ErrorActionPreference = "Stop"

$ComposeFile = Join-Path $PSScriptRoot "infra/compose/docker-compose.yml"
$BaseArgs = @("compose", "-f", $ComposeFile)

function Invoke-Compose {
    param([string[]]$ComposeArgs)
    & docker @BaseArgs @ComposeArgs
}

function Invoke-Gradle {
    param([string[]]$GradleArgs)
    & (Join-Path $PSScriptRoot "gradlew.bat") @GradleArgs
}

switch ($Target) {
    "up.core"      { Invoke-Compose @("--profile", "core", "up", "-d") }
    "up.sales"     { Invoke-Compose @("--profile", "sales", "up", "-d") }
    "up.stock"     { Invoke-Compose @("--profile", "stock", "up", "-d") }
    "up.money"     { Invoke-Compose @("--profile", "money", "up", "-d") }
    "up.analytics" { Invoke-Compose @("--profile", "analytics", "up", "-d") }
    "up.full"      { Invoke-Compose @("--profile", "full", "up", "-d") }

    "down" { Invoke-Compose @("--profile", "full", "down") }
    "ps"   { Invoke-Compose @("ps") }
    "logs" {
        if (-not $Service) { throw "Usage: .\make.ps1 logs -Service <name>" }
        Invoke-Compose @("logs", "-f", $Service)
    }

    "ports" {
        foreach ($p in 5432, 6379, 9092, 8123) {
            $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
            if ($conn) {
                $proc = Get-Process -Id $conn[0].OwningProcess -ErrorAction SilentlyContinue
                Write-Host ("{0,-6} IN USE by {1} (pid {2})" -f $p, $proc.ProcessName, $proc.Id)
            }
            else {
                Write-Host ("{0,-6} free" -f $p)
            }
        }
    }

    "build" { Invoke-Gradle @("build") }
    "test"  { Invoke-Gradle @("test") }

    "run.identity" { Invoke-Gradle @(":services:identity:bootRun") }
    "run.gateway"  { Invoke-Gradle @(":services:gateway:bootRun") }

    default {
        Write-Host "Targets:"
        Write-Host "  up.core up.sales up.stock up.money up.analytics up.full"
        Write-Host "  down ps ports logs -Service <name>"
        Write-Host "  build test run.identity run.gateway"
    }
}
