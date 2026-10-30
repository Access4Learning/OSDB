# SIF Unity Broker - Stop Script (PowerShell)
# This script stops all running broker services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SIF Unity Broker - Stopping Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to stop processes by window title pattern
function Stop-ServiceByTitle {
    param(
        [string]$TitlePattern,
        [string]$ServiceName
    )
    
    Write-Host "Stopping $ServiceName..." -ForegroundColor Yellow
    
    # Find processes with matching window title
    $processes = Get-Process | Where-Object { $_.MainWindowTitle -like "*$TitlePattern*" }
    
    if ($processes) {
        foreach ($proc in $processes) {
            try {
                Write-Host "  Found process: $($proc.MainWindowTitle) (PID: $($proc.Id))" -ForegroundColor Gray
                
                # Try graceful stop first
                $proc.CloseMainWindow() | Out-Null
                Start-Sleep -Milliseconds 500
                
                # Check if still running
                if (-not $proc.HasExited) {
                    Write-Host "  Waiting for graceful shutdown..." -ForegroundColor Gray
                    $proc.WaitForExit(10000) | Out-Null
                }
                
                # Force kill if still running
                if (-not $proc.HasExited) {
                    Write-Host "  Force stopping..." -ForegroundColor Red
                    Stop-Process -Id $proc.Id -Force
                }
                
                Write-Host "  $ServiceName stopped" -ForegroundColor Green
            } catch {
                Write-Host "  Error stopping process: $_" -ForegroundColor Red
            }
        }
    } else {
        Write-Host "  $ServiceName is not running" -ForegroundColor Gray
    }
}

# Function to stop Java processes by port
function Stop-ServiceByPort {
    param(
        [int]$Port,
        [string]$ServiceName
    )
    
    Write-Host "Stopping $ServiceName (port $Port)..." -ForegroundColor Yellow
    
    # Find process using the port
    $netstatOutput = netstat -ano | Select-String ":$Port\s" | Select-String "LISTENING"
    
    if ($netstatOutput) {
        foreach ($line in $netstatOutput) {
            # Extract PID from netstat output (last column)
            $processPid = ($line -split '\s+')[-1]
            
            if ($processPid -match '^\d+$') {
                try {
                    $process = Get-Process -Id $processPid -ErrorAction SilentlyContinue
                    if ($process) {
                        Write-Host "  Found process: $($process.ProcessName) (PID: $processPid)" -ForegroundColor Gray
                        
                        # Try graceful stop
                        Stop-Process -Id $processPid -ErrorAction SilentlyContinue
                        Start-Sleep -Seconds 2
                        
                        # Force kill if still running
                        if (Get-Process -Id $processPid -ErrorAction SilentlyContinue) {
                            Write-Host "  Force stopping..." -ForegroundColor Red
                            Stop-Process -Id $processPid -Force
                        }
                        
                        Write-Host "  $ServiceName stopped" -ForegroundColor Green
                    }
                } catch {
                    Write-Host "  Error stopping process: $_" -ForegroundColor Red
                }
            }
        }
    } else {
        Write-Host "  $ServiceName is not running" -ForegroundColor Gray
    }
}

Write-Host "Stopping services in reverse order..." -ForegroundColor Cyan
Write-Host ""

# Stop services in reverse order (Gateway first, Environment Service last)
Stop-ServiceByPort -Port 8080 -ServiceName "Gateway"
Stop-ServiceByPort -Port 8088 -ServiceName "Responses Connector"
Stop-ServiceByPort -Port 8087 -ServiceName "Services Connector"
Stop-ServiceByPort -Port 8086 -ServiceName "Job Service"
Stop-ServiceByPort -Port 8085 -ServiceName "Requests Connector"
Stop-ServiceByPort -Port 8084 -ServiceName "Events Connector"
Stop-ServiceByPort -Port 8083 -ServiceName "Subscription Service"
Stop-ServiceByPort -Port 8082 -ServiceName "Queue Service"
Stop-ServiceByPort -Port 8081 -ServiceName "Environment Service"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All services stopped" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Optional: Close any remaining PowerShell windows with service titles
Write-Host "Cleaning up service windows..." -ForegroundColor Yellow
$serviceWindows = Get-Process powershell | Where-Object { 
    $_.MainWindowTitle -match "Gateway|Environment Service|Queue Service|Subscription Service|Job Service|Requests Connector|Responses Connector|Events Connector|Services Connector"
}

if ($serviceWindows) {
    foreach ($window in $serviceWindows) {
        try {
            Stop-Process -Id $window.Id -Force -ErrorAction SilentlyContinue
        } catch {
            # Ignore errors
        }
    }
    Write-Host "Service windows closed" -ForegroundColor Green
} else {
    Write-Host "No service windows found" -ForegroundColor Gray
}

Write-Host ""
