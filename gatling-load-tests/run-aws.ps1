# Run Gatling load tests against AWS environment
# Loads credentials from ../aws.env (copy aws.env.example to aws.env and fill in values)

$envFile = Join-Path $PSScriptRoot "..\aws.env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#]' } | ForEach-Object {
        $parts = $_ -split '=', 2
        if ($parts.Length -eq 2) { [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process") }
    }
}

if (-not $env:GATEWAY_URL) { Write-Host "ERROR: Set GATEWAY_URL in ../aws.env (copy aws.env.example)" -ForegroundColor Red; exit 1 }
if (-not $env:ADMIN_SESSION_TOKEN) { Write-Host "ERROR: Set ADMIN_SESSION_TOKEN in ../aws.env" -ForegroundColor Red; exit 1 }
if (-not $env:ADMIN_SHARED_SECRET) { Write-Host "ERROR: Set ADMIN_SHARED_SECRET in ../aws.env" -ForegroundColor Red; exit 1 }

Write-Host "Load Test → $env:GATEWAY_URL" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/3] Setup (create domain, solution, environments, queues)..." -ForegroundColor Yellow
..\mvnw gatling:test "-Dgatling.simulationClass=org.sifunity.loadtest.simulation.SetupSimulation"
if ($LASTEXITCODE -ne 0) { Write-Host "Setup failed!" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "[2/3] Publish (send events)..." -ForegroundColor Yellow
..\mvnw gatling:test "-Dgatling.simulationClass=org.sifunity.loadtest.simulation.PublishSimulation"
if ($LASTEXITCODE -ne 0) { Write-Host "Publish failed!" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "[3/3] Consume (poll and acknowledge messages)..." -ForegroundColor Yellow
..\mvnw gatling:test "-Dgatling.simulationClass=org.sifunity.loadtest.simulation.ConsumeSimulation"
if ($LASTEXITCODE -ne 0) { Write-Host "Consume failed!" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "Load test complete! Reports in target/gatling/" -ForegroundColor Green
