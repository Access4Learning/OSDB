# SIF Unity Broker - Health Check Script (PowerShell)
# This script checks the health status of all broker services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SIF Unity Broker - Health Check" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to check service health
function Check-Service {
    param(
        [string]$ServiceName,
        [int]$Port
    )
    
    $url = "http://localhost:$Port/actuator/health"
    $status = "OFFLINE"
    $color = "Red"
    $symbol = "✗"
    
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            $healthData = $response.Content | ConvertFrom-Json
            if ($healthData.status -eq "UP") {
                $status = "UP"
                $color = "Green"
                $symbol = "✓"
            } else {
                $status = $healthData.status
                $color = "Yellow"
                $symbol = "⚠"
            }
        }
    } catch {
        if ($_.Exception.Response.StatusCode.Value__ -eq 503) {
            $status = "DOWN"
            $color = "Yellow"
            $symbol = "⚠"
        }
    }
    
    $servicePadded = $ServiceName.PadRight(25)
    Write-Host "$servicePadded " -NoNewline
    Write-Host "$symbol $status".PadRight(12) -ForegroundColor $color -NoNewline
    Write-Host "http://localhost:$Port"
    
    return ($status -eq "UP")
}

# Check infrastructure
Write-Host "Infrastructure:" -ForegroundColor Cyan
Write-Host ""

# Check PostgreSQL
$pgStatus = "OFFLINE"
$pgColor = "Red"
$pgSymbol = "✗"
try {
    $pgTest = psql -U sif_broker -d sif_broker -h localhost -c "SELECT 1;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        $pgStatus = "UP"
        $pgColor = "Green"
        $pgSymbol = "✓"
    }
} catch {
    # Keep default OFFLINE status
}

$pgPadded = "PostgreSQL".PadRight(25)
Write-Host "$pgPadded " -NoNewline
Write-Host "$pgSymbol $pgStatus".PadRight(12) -ForegroundColor $pgColor -NoNewline
Write-Host "localhost:5432"

# Check Redis
$redisStatus = "OFFLINE"
$redisColor = "Red"
$redisSymbol = "✗"
try {
    $redisTest = redis-cli ping 2>&1
    if ($redisTest -eq "PONG") {
        $redisStatus = "UP"
        $redisColor = "Green"
        $redisSymbol = "✓"
    }
} catch {
    # Keep default OFFLINE status
}

$redisPadded = "Redis".PadRight(25)
Write-Host "$redisPadded " -NoNewline
Write-Host "$redisSymbol $redisStatus".PadRight(12) -ForegroundColor $redisColor -NoNewline
Write-Host "localhost:6379"

Write-Host ""
Write-Host "Services:" -ForegroundColor Cyan
Write-Host ""

# Check all services
$upCount = 0
$downCount = 0

$services = @(
    @{Name="Gateway"; Port=8080},
    @{Name="Environment Service"; Port=8081},
    @{Name="Queue Service"; Port=8082},
    @{Name="Subscription Service"; Port=8083},
    @{Name="Job Service"; Port=8084},
    @{Name="Requests Connector"; Port=8085},
    @{Name="Responses Connector"; Port=8086},
    @{Name="Events Connector"; Port=8087},
    @{Name="Services Connector"; Port=8088}
)

foreach ($service in $services) {
    $isUp = Check-Service -ServiceName $service.Name -Port $service.Port
    if ($isUp) {
        $upCount++
    } else {
        $downCount++
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Status: " -NoNewline
Write-Host "$upCount UP" -ForegroundColor Green -NoNewline
Write-Host " / " -NoNewline
Write-Host "$downCount DOWN" -ForegroundColor Red -NoNewline
Write-Host " / 9 Total"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Exit with error if any service is down
if ($downCount -gt 0) {
    exit 1
}

exit 0
