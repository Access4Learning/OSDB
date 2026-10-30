# Run Gatling load tests against local broker
$env:GATEWAY_URL = "http://localhost:8080"
$env:ADMIN_SESSION_TOKEN = "admin-bootstrap-token"
$env:ADMIN_SHARED_SECRET = "admin-bootstrap-secret"

# Load test parameters (override as needed)
$env:ZONE_COUNT = "300"
$env:DURATION_MINUTES = "5"
$env:RECORDS_PER_EVENT = "25"
$env:THREAD_COUNT = "10"
$env:CONNECTIONS_PER_CONSUMER = "3"

Write-Host "Load Test → LOCAL ($env:GATEWAY_URL)" -ForegroundColor Green
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
