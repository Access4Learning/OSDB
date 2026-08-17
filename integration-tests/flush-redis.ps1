# PowerShell script to flush Redis cache for integration tests
# This script connects to the Redis container and flushes all databases

Write-Host "Flushing Redis cache for integration tests..." -ForegroundColor Yellow

# Try to find the Redis container used by Testcontainers
$redisContainer = docker ps --filter "ancestor=redis:7-alpine" --format "{{.ID}}" | Select-Object -First 1

if ($null -eq $redisContainer -or $redisContainer -eq "") {
    Write-Host "Redis container not found." -ForegroundColor Yellow
    Write-Host "The Redis container will be created fresh when you run the tests." -ForegroundColor Green
    exit 0
}

Write-Host "Found Redis container: $redisContainer" -ForegroundColor Green
Write-Host "Executing FLUSHALL command..." -ForegroundColor Yellow

# Execute FLUSHALL command in the Redis container
docker exec $redisContainer redis-cli FLUSHALL

if ($LASTEXITCODE -eq 0) {
    Write-Host "Redis cache flushed successfully!" -ForegroundColor Green
    Write-Host "You can now run the integration tests." -ForegroundColor Green
} else {
    Write-Host "Failed to flush Redis cache" -ForegroundColor Red
    Write-Host "You may need to restart the Redis container manually:" -ForegroundColor Yellow
    Write-Host "  docker restart $redisContainer" -ForegroundColor Yellow
    exit 1
}
