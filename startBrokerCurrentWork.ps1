# SIF Unity Broker - Full Startup with Observability
# Starts PostgreSQL, Redis, Observability Stack (Prometheus, Grafana, Jaeger, ELK),
# then all 8 microservices with the local profile.

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SIF Unity Broker - Full Startup" -ForegroundColor Cyan
Write-Host "(All Services + Observability Stack)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Internal service token required by all services for inter-service auth
$env:INTERNAL_SERVICE_TOKEN = "local-dev-internal-token-do-not-use-in-prod"

# Build common library and all modules first
Write-Host "Building all modules..." -ForegroundColor Yellow
Set-Location $PSScriptRoot
mvn install -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed! Check errors above." -ForegroundColor Red
    exit 1
}
Write-Host "Build complete" -ForegroundColor Green
Write-Host ""

# Check if PostgreSQL is running
Write-Host "Checking PostgreSQL..." -ForegroundColor Yellow
$pgContainer = docker ps --filter "name=queue-service-postgres" --format "{{.Names}}" 2>$null
if ($pgContainer -ne "queue-service-postgres") {
    Write-Host "Starting PostgreSQL and Redis..." -ForegroundColor Yellow
    Set-Location "$PSScriptRoot\queue-service"
    docker-compose up -d postgres redis 2>$null
    Set-Location $PSScriptRoot

    # Wait for PostgreSQL
    Write-Host "Waiting for PostgreSQL..." -ForegroundColor Yellow
    $attempts = 0
    while ($attempts -lt 30) {
        $check = docker exec queue-service-postgres pg_isready -U postgres 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        $attempts++
        Start-Sleep -Seconds 2
    }

    # Ensure DB and user exist
    docker exec queue-service-postgres psql -U postgres -c "DO `$`$ BEGIN IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sif_broker') THEN CREATE ROLE sif_broker WITH LOGIN PASSWORD 'changeme'; END IF; END `$`$;" 2>$null
    docker exec queue-service-postgres psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'sif_broker'" 2>$null | Select-String "1" >$null
    if ($LASTEXITCODE -ne 0) {
        docker exec queue-service-postgres psql -U postgres -c "CREATE DATABASE sif_broker OWNER sif_broker;" 2>$null
    }
    docker exec queue-service-postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE sif_broker TO sif_broker;" 2>$null
    docker exec queue-service-postgres psql -U postgres -d sif_broker -c "GRANT ALL ON SCHEMA public TO sif_broker;" 2>$null
}
Write-Host "PostgreSQL is running" -ForegroundColor Green

# Check Redis
$redisContainer = docker ps --filter "name=queue-service-redis" --format "{{.Names}}" 2>$null
if ($redisContainer -eq "queue-service-redis") {
    Write-Host "Redis is running" -ForegroundColor Green
} else {
    Write-Host "Redis not running - some features may not work" -ForegroundColor Yellow
}

Write-Host ""

# Start Observability Stack (Prometheus, Grafana, Jaeger — no ELK for local dev)
Write-Host "Starting Observability Stack (Prometheus, Grafana, Jaeger)..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot\observability\docker"
docker-compose up -d jaeger prometheus grafana 2>$null
Set-Location $PSScriptRoot

# Wait for Prometheus
Write-Host "Waiting for Prometheus..." -ForegroundColor Yellow
$attempts = 0
while ($attempts -lt 20) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9090/-/ready" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        Write-Host "Prometheus is ready" -ForegroundColor Green
        break
    } catch {
        $attempts++
        Start-Sleep -Seconds 2
    }
}

# Wait for Grafana
Write-Host "Waiting for Grafana..." -ForegroundColor Yellow
$attempts = 0
while ($attempts -lt 20) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:3000/api/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        Write-Host "Grafana is ready" -ForegroundColor Green
        break
    } catch {
        $attempts++
        Start-Sleep -Seconds 2
    }
}

# Check Jaeger
$jaegerContainer = docker ps --filter "name=sif-jaeger" --format "{{.Names}}" 2>$null
if ($jaegerContainer -eq "sif-jaeger") {
    Write-Host "Jaeger is running" -ForegroundColor Green
} else {
    Write-Host "Jaeger not running - tracing will be unavailable" -ForegroundColor Yellow
}

Write-Host ""

# 1. Start Environment Service
Write-Host "[1/8] Starting Environment Service (port 8081)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\environment-service'; Write-Host 'Environment Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"

# Wait for Environment Service
Write-Host "Waiting for Environment Service..." -ForegroundColor Yellow
$attempts = 0
$ready = $false
while ($attempts -lt 30 -and -not $ready) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        $ready = $true
        Write-Host "Environment Service is ready" -ForegroundColor Green
    } catch [System.Net.WebException] {
        $statusCode = [int]$_.Exception.Response.StatusCode
        if ($statusCode -eq 503) {
            $ready = $true
            Write-Host "Environment Service is running (some health checks failing)" -ForegroundColor Yellow
        } else {
            $attempts++
            Start-Sleep -Seconds 2
        }
    } catch {
        $attempts++
        Start-Sleep -Seconds 2
    }
}

if (-not $ready) {
    Write-Host "Environment Service failed to start - check its window" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 2. Start Queue Service
Write-Host "[2/8] Starting Queue Service (port 8082)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\queue-service'; Write-Host 'Queue Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 3. Start Subscription Service
Write-Host "[3/8] Starting Subscription Service (port 8083)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\subscription-service'; Write-Host 'Subscription Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 4. Start Events Connector
Write-Host "[4/8] Starting Events Connector (port 8084)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\events-connector'; Write-Host 'Events Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 5. Start Services Connector
Write-Host "[5/8] Starting Services Connector (port 8087)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\services-connector'; Write-Host 'Services Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 6. Start Responses Connector
Write-Host "[6/8] Starting Responses Connector (port 8088)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\responses-connector'; Write-Host 'Responses Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 7. Start Requests Connector
Write-Host "[7/8] Starting Requests Connector (port 8085)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\requests-connector'; Write-Host 'Requests Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 8. Start Gateway
Write-Host "[8/8] Starting Gateway (port 8080)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\gateway'; Write-Host 'Gateway' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Full broker + observability starting!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Services:" -ForegroundColor Yellow
Write-Host "  Environment Service:    http://localhost:8081/actuator/health" -ForegroundColor White
Write-Host "  Queue Service:          http://localhost:8082/actuator/health" -ForegroundColor White
Write-Host "  Subscription Service:   http://localhost:8083/actuator/health" -ForegroundColor White
Write-Host "  Events Connector:       http://localhost:8084/actuator/health" -ForegroundColor White
Write-Host "  Requests Connector:     http://localhost:8085/actuator/health" -ForegroundColor White
Write-Host "  Services Connector:     http://localhost:8087/actuator/health" -ForegroundColor White
Write-Host "  Responses Connector:    http://localhost:8088/actuator/health" -ForegroundColor White
Write-Host "  Gateway:                http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "Observability:" -ForegroundColor Yellow
Write-Host "  Prometheus:             http://localhost:9090" -ForegroundColor White
Write-Host "  Grafana:                http://localhost:3000  (admin/admin)" -ForegroundColor White
Write-Host "  Jaeger UI:              http://localhost:16686" -ForegroundColor White
Write-Host ""
Write-Host "Metrics Endpoints (raw):" -ForegroundColor Yellow
Write-Host "  Gateway metrics:        http://localhost:8080/actuator/prometheus" -ForegroundColor White
Write-Host "  Events metrics:         http://localhost:8084/actuator/prometheus" -ForegroundColor White
Write-Host "  Queue metrics:          http://localhost:8082/actuator/prometheus" -ForegroundColor White
Write-Host ""
Write-Host "Wait ~30 seconds for Gateway to be ready." -ForegroundColor Yellow
Write-Host "Grafana dashboards auto-provision on startup." -ForegroundColor Yellow
Write-Host ""
