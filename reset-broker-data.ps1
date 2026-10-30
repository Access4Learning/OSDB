# SIF Unity Broker - Reset All Data
# Clears Redis, PostgreSQL (sif_broker DB), and filesystem message storage

Write-Host "========================================" -ForegroundColor Red
Write-Host "  RESET ALL BROKER DATA" -ForegroundColor Red
Write-Host "========================================" -ForegroundColor Red
Write-Host ""

$confirm = Read-Host "This will DELETE all data in Redis, PostgreSQL, and local message files. Continue? (y/N)"
if ($confirm -ne 'y') {
    Write-Host "Aborted." -ForegroundColor Yellow
    exit 0
}

Write-Host ""

# 1. Redis FLUSHALL
Write-Host "[1/3] Flushing Redis..." -ForegroundColor Yellow
docker exec queue-service-redis redis-cli FLUSHALL 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  Redis flushed" -ForegroundColor Green
} else {
    Write-Host "  Redis flush failed (is container running?)" -ForegroundColor Red
}

# 2. PostgreSQL - drop and recreate all tables
Write-Host "[2/3] Resetting PostgreSQL (sif_broker)..." -ForegroundColor Yellow
$sql = @"
DO `$`$ DECLARE r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END `$`$;
"@
docker exec queue-service-postgres psql -U sif_broker -d sif_broker -c "$sql" 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  PostgreSQL tables dropped" -ForegroundColor Green
    Write-Host "  (Flyway will recreate on next service start)" -ForegroundColor Gray
} else {
    Write-Host "  PostgreSQL reset failed (is container running?)" -ForegroundColor Red
}

# 3. Filesystem message storage
Write-Host "[3/3] Clearing local message files..." -ForegroundColor Yellow
$msgDirs = @(
    "$PSScriptRoot\queue-service\data\messages"
)
foreach ($dir in $msgDirs) {
    if (Test-Path $dir) {
        Remove-Item -Recurse -Force $dir
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "  Cleared: $dir" -ForegroundColor Green
    } else {
        Write-Host "  Not found (skip): $dir" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Data reset complete." -ForegroundColor Green
Write-Host "  Restart all services before testing." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Green
