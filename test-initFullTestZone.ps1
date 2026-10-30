# SIF Unity Broker - Initialize Full Test Zone
# This script creates a complete test zone with contexts for testing

param(
    [string]$ZoneName = "test-zone",
    [string]$BaseUrl = "http://localhost:8081",
    [string]$SessionToken = "admin-bootstrap-token",
    [string]$SharedSecret = "admin-bootstrap-secret"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SIF Unity Broker - Initialize Test Zone" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to create HMAC-SHA256 signature
function Get-HmacSignature {
    param(
        [string]$message,
        [string]$secret
    )
    
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
    $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($message))
    return [Convert]::ToBase64String($hash)
}

# Function to make authenticated request
function Invoke-AuthenticatedRequest {
    param(
        [string]$Method,
        [string]$Url,
        [string]$SessionToken,
        [string]$SharedSecret,
        [string]$Body = $null,
        [switch]$Silent
    )
    
    # Create timestamp (ISO 8601 format)
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    
    # Create message to sign
    $uri = [System.Uri]$Url
    $path = $uri.PathAndQuery
    $messageToSign = "$Method`n$path`n$timestamp"
    
    if ($Body) {
        $messageToSign += "`n$Body"
    }
    
    # Calculate HMAC signature
    $signature = Get-HmacSignature -message $messageToSign -secret $SharedSecret
    
    # Create Authorization header
    $authHeader = "SIF_HMACSHA256 ${SessionToken}:${signature}"
    
    # Make request
    $headers = @{
        "Authorization" = $authHeader
        "timestamp" = $timestamp
        "Content-Type" = "application/json"
    }
    
    try {
        if ($Body) {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Body $Body
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers
        }
        
        if (-not $Silent) {
            Write-Host "Success" -ForegroundColor Green
        }
        return $response
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd()
            Write-Host "  Response: $responseBody" -ForegroundColor Red
        }
        return $null
    }
}

Write-Host "Script loaded successfully" -ForegroundColor Green

# Step 1: Check if zone already exists
Write-Host "Step 1: Checking if zone '$ZoneName' already exists..." -ForegroundColor Yellow
$existingZones = Invoke-AuthenticatedRequest `
    -Method "GET" `
    -Url "$BaseUrl/zones" `
    -SessionToken $SessionToken `
    -SharedSecret $SharedSecret `
    -Silent

$zoneExists = $false
if ($existingZones) {
    $existingZone = $existingZones | Where-Object { $_.zoneId -eq $ZoneName }
    if ($existingZone) {
        $zoneExists = $true
        Write-Host "  Zone '$ZoneName' already exists (ID: $($existingZone.id))" -ForegroundColor Yellow
    } else {
        Write-Host "  Zone '$ZoneName' does not exist" -ForegroundColor Gray
    }
}

Write-Host ""

# Step 2: Create zone if it doesn't exist
if (-not $zoneExists) {
    Write-Host "Step 2: Creating zone '$ZoneName'..." -ForegroundColor Yellow
    
    $zoneBody = @{
        zoneId = $ZoneName
        description = "Test zone for development and testing purposes"
    } | ConvertTo-Json
    
    $zone = Invoke-AuthenticatedRequest `
        -Method "POST" `
        -Url "$BaseUrl/zones" `
        -SessionToken $SessionToken `
        -SharedSecret $SharedSecret `
        -Body $zoneBody
    
    if ($zone) {
        Write-Host "  Zone created successfully!" -ForegroundColor Green
        Write-Host "    Zone ID: $($zone.zoneId)" -ForegroundColor Gray
        Write-Host "    UUID: $($zone.id)" -ForegroundColor Gray
        Write-Host "    Description: $($zone.description)" -ForegroundColor Gray
    } else {
        Write-Host "  Failed to create zone. Exiting." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Step 2: Skipping zone creation (already exists)" -ForegroundColor Gray
}

Write-Host ""

# Step 3: Create contexts within the zone
Write-Host "Step 3: Creating contexts within zone '$ZoneName'..." -ForegroundColor Yellow

$contexts = @(
    @{
        contextId = "test-context-1"
        description = "First test context for basic testing"
    },
    @{
        contextId = "test-context-2"
        description = "Second test context for multi-context scenarios"
    },
    @{
        contextId = "production-sim"
        description = "Context simulating production environment"
    },
    @{
        contextId = "staging-sim"
        description = "Context simulating staging environment"
    }
)

$createdContexts = @()

foreach ($contextDef in $contexts) {
    Write-Host "  Creating context '$($contextDef.contextId)'..." -ForegroundColor Cyan
    
    # Check if context already exists
    $existingContexts = Invoke-AuthenticatedRequest `
        -Method "GET" `
        -Url "$BaseUrl/zones/$ZoneName/contexts" `
        -SessionToken $SessionToken `
        -SharedSecret $SharedSecret `
        -Silent
    
    $contextExists = $false
    if ($existingContexts) {
        $existingContext = $existingContexts | Where-Object { $_.contextId -eq $contextDef.contextId }
        if ($existingContext) {
            $contextExists = $true
            Write-Host "    Context already exists (ID: $($existingContext.id))" -ForegroundColor Yellow
            $createdContexts += $existingContext
        }
    }
    
    if (-not $contextExists) {
        $contextBody = @{
            contextId = $contextDef.contextId
            description = $contextDef.description
        } | ConvertTo-Json
        
        $context = Invoke-AuthenticatedRequest `
            -Method "POST" `
            -Url "$BaseUrl/zones/$ZoneName/contexts" `
            -SessionToken $SessionToken `
            -SharedSecret $SharedSecret `
            -Body $contextBody
        
        if ($context) {
            Write-Host "    Context created successfully!" -ForegroundColor Green
            Write-Host "      Context ID: $($context.contextId)" -ForegroundColor Gray
            Write-Host "      UUID: $($context.id)" -ForegroundColor Gray
            $createdContexts += $context
        }
    }
}

Write-Host ""

# Step 4: Verify zone and contexts
Write-Host "Step 4: Verifying zone and contexts..." -ForegroundColor Yellow

Write-Host "  Listing all zones..." -ForegroundColor Cyan
$allZones = Invoke-AuthenticatedRequest `
    -Method "GET" `
    -Url "$BaseUrl/zones" `
    -SessionToken $SessionToken `
    -SharedSecret $SharedSecret `
    -Silent

if ($allZones) {
    Write-Host "    Total zones: $($allZones.Count)" -ForegroundColor Gray
    $testZone = $allZones | Where-Object { $_.zoneId -eq $ZoneName }
    if ($testZone) {
        Write-Host "    Zone '$ZoneName' found" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "  Listing contexts in zone '$ZoneName'..." -ForegroundColor Cyan
$allContexts = Invoke-AuthenticatedRequest `
    -Method "GET" `
    -Url "$BaseUrl/zones/$ZoneName/contexts" `
    -SessionToken $SessionToken `
    -SharedSecret $SharedSecret `
    -Silent

if ($allContexts) {
    Write-Host "    Total contexts: $($allContexts.Count)" -ForegroundColor Gray
    foreach ($ctx in $allContexts) {
        Write-Host "      - $($ctx.contextId): $($ctx.description)" -ForegroundColor Gray
    }
}

Write-Host ""

# Step 5: Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Initialization Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Zone Details:" -ForegroundColor Yellow
Write-Host "  Zone ID: $ZoneName" -ForegroundColor White
Write-Host "  Contexts: $($allContexts.Count)" -ForegroundColor White
Write-Host ""
Write-Host "Available Contexts:" -ForegroundColor Yellow
foreach ($ctx in $allContexts) {
    Write-Host "  - $($ctx.contextId)" -ForegroundColor White
}
Write-Host ""

# Optional: Export zone info to JSON file
if ($testZone -and $allContexts) {
    $zoneInfo = @{
        zoneName = $ZoneName
        zoneId = $testZone.id
        contexts = $allContexts | ForEach-Object {
            @{
                contextId = $_.contextId
                id = $_.id
                description = $_.description
            }
        }
        createdAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    }

    $zoneInfoJson = $zoneInfo | ConvertTo-Json -Depth 10
    $zoneInfoJson | Out-File -FilePath "test-zone-info.json" -Encoding UTF8

    Write-Host "Zone information saved to: test-zone-info.json" -ForegroundColor Cyan
}

Write-Host ""
