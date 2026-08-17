# Docker Configuration Validation Script for Subscription Service (PowerShell)

Write-Host "=== Docker Configuration Validation ===" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is installed
try {
    $dockerVersion = docker --version
    Write-Host "✅ Docker is installed: $dockerVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not installed" -ForegroundColor Red
    exit 1
}

# Check if Docker Compose is installed
try {
    $composeVersion = docker-compose --version
    Write-Host "✅ Docker Compose is installed: $composeVersion" -ForegroundColor Green
} catch {
    try {
        $composeVersion = docker compose version
        Write-Host "✅ Docker Compose is installed: $composeVersion" -ForegroundColor Green
    } catch {
        Write-Host "❌ Docker Compose is not installed" -ForegroundColor Red
        exit 1
    }
}

# Validate docker-compose.yml syntax
Write-Host ""
Write-Host "Validating docker-compose.yml..." -ForegroundColor Yellow
try {
    docker-compose config | Out-Null
    Write-Host "✅ docker-compose.yml is valid" -ForegroundColor Green
} catch {
    Write-Host "❌ docker-compose.yml has syntax errors" -ForegroundColor Red
    exit 1
}

# Check required ports are available
Write-Host ""
Write-Host "Checking port availability..." -ForegroundColor Yellow
$ports = @(8083, 5433, 6380, 8082)
foreach ($port in $ports) {
    $connection = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connection) {
        Write-Host "⚠️  Port $port is already in use" -ForegroundColor Yellow
    } else {
        Write-Host "✅ Port $port is available" -ForegroundColor Green
    }
}

# Validate mockserver configuration
Write-Host ""
Write-Host "Validating mockserver configuration..." -ForegroundColor Yellow
if (Test-Path "docker/mockserver-init.json") {
    try {
        $json = Get-Content "docker/mockserver-init.json" -Raw | ConvertFrom-Json
        Write-Host "✅ mockserver-init.json is valid JSON" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  mockserver-init.json may have JSON syntax issues" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ mockserver-init.json not found" -ForegroundColor Red
    exit 1
}

# Check if Dockerfile exists
Write-Host ""
Write-Host "Checking Dockerfile..." -ForegroundColor Yellow
if (Test-Path "Dockerfile") {
    Write-Host "✅ Dockerfile exists" -ForegroundColor Green
} else {
    Write-Host "❌ Dockerfile not found" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Validation Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "To build and run the services:" -ForegroundColor White
Write-Host "  docker-compose up -d" -ForegroundColor Gray
Write-Host ""
Write-Host "To test the API:" -ForegroundColor White
Write-Host "  curl http://localhost:8083/actuator/health" -ForegroundColor Gray
Write-Host ""
