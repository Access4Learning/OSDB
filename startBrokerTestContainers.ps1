#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Runs the full Maven build with Testcontainers, mimicking the Bitbucket pipeline environment.

.DESCRIPTION
    This script runs 'mvn clean install' using the same test profile and Testcontainers
    configuration as the CI pipeline. It requires Docker Desktop to be running.

    Tests use Testcontainers to spin up PostgreSQL and Redis containers automatically.
    No manual database or Redis setup is needed.

.EXAMPLE
    .\startBrokerTestContainers.ps1
    
.EXAMPLE
    # Run only a specific module
    .\startBrokerTestContainers.ps1 -Module environment-service

.EXAMPLE
    # Run with detailed Maven output
    .\startBrokerTestContainers.ps1 -DetailedOutput
#>

param(
    [Parameter(Mandatory=$false)]
    [string]$Module = "",
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipTests,
    
    [Parameter(Mandatory=$false)]
    [switch]$DetailedOutput
)

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  SIF Unity Broker - Testcontainers Build"   -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Check Docker is running
Write-Host "Checking Docker..." -ForegroundColor Yellow
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker is not running. Please start Docker Desktop." -ForegroundColor Red
        exit 1
    }
    Write-Host "Docker is running." -ForegroundColor Green
} catch {
    Write-Host "ERROR: Docker command not found. Please install Docker Desktop." -ForegroundColor Red
    exit 1
}

# Check Java version
Write-Host "Checking Java..." -ForegroundColor Yellow
java -version 2>&1 | Select-Object -First 1 | Write-Host
Write-Host ""

# Set Testcontainers environment (same as CI pipeline)
$env:TESTCONTAINERS_RYUK_DISABLED = "true"

# Build Maven command
$mvnArgs = @("clean", "install")

if ($Module -ne "") {
    $mvnArgs += "-pl"
    $mvnArgs += $Module
    $mvnArgs += "-am"
    Write-Host "Building module: $Module (with dependencies)" -ForegroundColor Yellow
} else {
    Write-Host "Building all modules" -ForegroundColor Yellow
}

if ($SkipTests) {
    $mvnArgs += "-DskipTests"
    Write-Host "Tests: SKIPPED" -ForegroundColor Yellow
} else {
    Write-Host "Tests: ENABLED (using Testcontainers)" -ForegroundColor Yellow
}

if (-not $DetailedOutput) {
    $mvnArgs += "--no-transfer-progress"
}

Write-Host ""
Write-Host "Running: mvn $($mvnArgs -join ' ')" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Run Maven
$startTime = Get-Date
& mvn @mvnArgs
$exitCode = $LASTEXITCODE
$duration = (Get-Date) - $startTime

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Build completed in $([math]::Round($duration.TotalMinutes, 1)) minutes" -ForegroundColor Cyan
if ($exitCode -eq 0) {
    Write-Host "  Result: SUCCESS" -ForegroundColor Green
} else {
    Write-Host "  Result: FAILURE (exit code: $exitCode)" -ForegroundColor Red
}
Write-Host "============================================" -ForegroundColor Cyan

exit $exitCode
