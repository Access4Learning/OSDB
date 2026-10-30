# SIF Unity Broker - Local Startup Script (PowerShell)
# This script starts all broker services in the correct order

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SIF Unity Broker - Local Startup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Internal service token required by all services for inter-service auth
$env:INTERNAL_SERVICE_TOKEN = "local-dev-internal-token-do-not-use-in-prod"

# Build all modules first so sif-broker-common (and every service) is installed
# to the local .m2 repository. Services resolve sif-broker-common as a dependency,
# so on a fresh clone `mvn spring-boot:run` fails without this step.
Write-Host "Building all modules (mvn install -DskipTests)..." -ForegroundColor Yellow
mvn install -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed! Check errors above." -ForegroundColor Red
    exit 1
}
Write-Host "Build complete" -ForegroundColor Green
Write-Host ""

# Check if PostgreSQL is running
Write-Host "Checking PostgreSQL..." -ForegroundColor Yellow
try {
    # Check if PostgreSQL container is running
    $pgContainer = docker ps --filter "name=queue-service-postgres" --format "{{.Names}}" 2>$null
    if ($pgContainer -eq "queue-service-postgres") {
        # Test database connection using Docker
        $pgTest = docker exec queue-service-postgres psql -U postgres -d sif_broker -c "SELECT 1;" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "PostgreSQL is running" -ForegroundColor Green
        } else {
            Write-Host "PostgreSQL connection failed" -ForegroundColor Red
            Write-Host "  Database or user may not exist. Creating..." -ForegroundColor Yellow
            # Create user and grant permissions if they don't exist
            docker exec queue-service-postgres psql -U postgres -c "CREATE USER IF NOT EXISTS sif_broker WITH ENCRYPTED PASSWORD 'changeme';" 2>$null
            docker exec queue-service-postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE sif_broker TO sif_broker;" 2>$null
            Write-Host "PostgreSQL setup complete" -ForegroundColor Green
        }
    } else {
        Write-Host "PostgreSQL container not running" -ForegroundColor Red
        Write-Host "  Starting PostgreSQL with Docker..." -ForegroundColor Yellow
        Set-Location queue-service
        docker-compose up -d postgres redis 2>$null
        Set-Location ..
        
        # Wait for PostgreSQL to be healthy before proceeding
        Write-Host "  Waiting for PostgreSQL to be ready..." -ForegroundColor Yellow
        $pgAttempts = 0
        $pgMaxAttempts = 30
        $pgReady = $false
        while ($pgAttempts -lt $pgMaxAttempts -and -not $pgReady) {
            $pgCheck = docker exec queue-service-postgres pg_isready -U postgres 2>$null
            if ($LASTEXITCODE -eq 0) {
                $pgReady = $true
            } else {
                $pgAttempts++
                Start-Sleep -Seconds 2
            }
        }
        
        if (-not $pgReady) {
            Write-Host "  PostgreSQL failed to start in time" -ForegroundColor Red
            exit 1
        }
        
        # Ensure sif_broker user and database exist
        Write-Host "  Ensuring sif_broker user and database exist..." -ForegroundColor Yellow
        docker exec queue-service-postgres psql -U postgres -c "DO `$`$ BEGIN IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sif_broker') THEN CREATE ROLE sif_broker WITH LOGIN PASSWORD 'changeme'; END IF; END `$`$;" 2>$null
        docker exec queue-service-postgres psql -U postgres -c "SELECT 1 FROM pg_database WHERE datname = 'sif_broker'" 2>$null | Select-String "1" >$null
        if ($LASTEXITCODE -ne 0) {
            docker exec queue-service-postgres psql -U postgres -c "CREATE DATABASE sif_broker OWNER sif_broker;" 2>$null
        }
        docker exec queue-service-postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE sif_broker TO sif_broker;" 2>$null
        docker exec queue-service-postgres psql -U postgres -d sif_broker -c "GRANT ALL ON SCHEMA public TO sif_broker;" 2>$null
        
        Write-Host "PostgreSQL started and configured" -ForegroundColor Green
    }
} catch {
    Write-Host "PostgreSQL not accessible" -ForegroundColor Red
    Write-Host "  Please ensure Docker is running and try again" -ForegroundColor Red
    exit 1
}

# Check if Redis is running
Write-Host "Checking Redis..." -ForegroundColor Yellow
try {
    # Check if Redis container is running
    $redisContainer = docker ps --filter "name=queue-service-redis" --format "{{.Names}}" 2>$null
    if ($redisContainer -eq "queue-service-redis") {
        # Test Redis connection using Docker
        $redisTest = docker exec queue-service-redis redis-cli ping 2>$null
        if ($redisTest -eq "PONG") {
            Write-Host "Redis is running" -ForegroundColor Green
        } else {
            Write-Host "Redis connection failed" -ForegroundColor Red
            Write-Host "  Please check Redis container" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "Redis container not running" -ForegroundColor Red
        Write-Host "  Redis should have been started with PostgreSQL" -ForegroundColor Yellow
        exit 1
    }
} catch {
    Write-Host "Redis not accessible" -ForegroundColor Red
    Write-Host "  Please ensure Docker is running and try again" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Starting services..." -ForegroundColor Cyan
Write-Host ""

# 1. Start Environment Service (FIRST - required by all others)
Write-Host "[1/9] Starting Environment Service (port 8081)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\environment-service'; Write-Host 'Environment Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 5

# Wait for Environment Service to be ready
Write-Host "Waiting for Environment Service to start..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 0
$envServiceReady = $false

while ($attempt -lt $maxAttempts -and -not $envServiceReady) {
    try {
        # Try to connect - we accept any response (200 or 503) as "service is up"
        $response = Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        # Got a response - service is up
        $envServiceReady = $true
        if ($response.StatusCode -eq 503) {
            Write-Host "Environment Service is running (some health checks may be failing)" -ForegroundColor Yellow
        } else {
            Write-Host "Environment Service is ready" -ForegroundColor Green
        }
    } catch [System.Net.WebException] {
        # Check if we got an HTTP response (even if error status)
        $statusCode = [int]$_.Exception.Response.StatusCode
        if ($statusCode -eq 503) {
            # 503 means service is up but not fully healthy - that's OK
            $envServiceReady = $true
            Write-Host "Environment Service is running (some health checks may be failing)" -ForegroundColor Yellow
        } else {
            # Other error or no connection - keep trying
            $attempt++
            Start-Sleep -Seconds 2
        }
    } catch {
        # Connection failed - keep trying
        $attempt++
        Start-Sleep -Seconds 2
    }
}

if (-not $envServiceReady) {
    Write-Host "Environment Service failed to start" -ForegroundColor Red
    Write-Host "  Check the Environment Service window for errors" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 2. Start Queue Service
Write-Host "[2/9] Starting Queue Service (port 8082)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\queue-service'; Write-Host 'Queue Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 3. Start Subscription Service
Write-Host "[3/9] Starting Subscription Service (port 8083)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\subscription-service'; Write-Host 'Subscription Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 4. Start Job Service
Write-Host "[4/9] Starting Job Service (port 8086)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\job-service'; Write-Host 'Job Service' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "Waiting for core services to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 5. Start Requests Connector
Write-Host "[5/9] Starting Requests Connector (port 8085)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\requests-connector'; Write-Host 'Requests Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 6. Start Responses Connector
Write-Host "[6/9] Starting Responses Connector (port 8088)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\responses-connector'; Write-Host 'Responses Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 7. Start Events Connector
Write-Host "[7/9] Starting Events Connector (port 8084)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\events-connector'; Write-Host 'Events Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

# 8. Start Services Connector
Write-Host "[8/9] Starting Services Connector (port 8087)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\services-connector'; Write-Host 'Services Connector' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "Waiting for connectors to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 9. Start Gateway (LAST - routes to all other services)
Write-Host "[9/9] Starting Gateway (port 8080)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\gateway'; Write-Host 'Gateway' -ForegroundColor Cyan; mvn spring-boot:run '-Dspring-boot.run.profiles=local' '-Dmaven.test.skip=true'"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All services are starting!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Service Windows:" -ForegroundColor Yellow
Write-Host "  - Check each PowerShell window for startup status" -ForegroundColor White
Write-Host "  - Look for 'Started <ServiceName>Application' message" -ForegroundColor White
Write-Host ""
Write-Host "Health Checks (wait ~60 seconds for all services):" -ForegroundColor Yellow
Write-Host "  Gateway:              http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  Environment Service:  http://localhost:8081/actuator/health" -ForegroundColor White
Write-Host "  Queue Service:        http://localhost:8082/actuator/health" -ForegroundColor White
Write-Host "  Subscription Service: http://localhost:8083/actuator/health" -ForegroundColor White
Write-Host "  Job Service:          http://localhost:8086/actuator/health" -ForegroundColor White
Write-Host "  Requests Connector:   http://localhost:8085/actuator/health" -ForegroundColor White
Write-Host "  Responses Connector:  http://localhost:8088/actuator/health" -ForegroundColor White
Write-Host "  Events Connector:     http://localhost:8084/actuator/health" -ForegroundColor White
Write-Host "  Services Connector:   http://localhost:8087/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "Admin Credentials (HMAC-SHA256):" -ForegroundColor Yellow
Write-Host "  Session Token:  admin-bootstrap-token   (override with ADMIN_SESSION_TOKEN)" -ForegroundColor White
Write-Host "  Shared Secret:  admin-bootstrap-secret  (override with ADMIN_SHARED_SECRET)" -ForegroundColor White
Write-Host "  Auth is HMAC-signed (no username/password). See API-AUTHENTICATION-GUIDE.md." -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C in each service window to stop services" -ForegroundColor Yellow
Write-Host ""
