# SIF Unity Broker - API Authentication Test Script
# This script demonstrates how to authenticate and make requests to the broker

# Admin credentials from bootstrap
$sessionToken = "admin-bootstrap-token"
$sharedSecret = "admin-bootstrap-secret"

# Base URLs
$envServiceUrl = "http://localhost:8081"
$gatewayUrl = "http://localhost:8080"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SIF Unity Broker - API Authentication" -ForegroundColor Cyan
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
        [string]$Body = $null
    )
    
    # Create timestamp (ISO 8601 format)
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    
    # Create message to sign: METHOD\nURL\nTIMESTAMP\nBODY
    $uri = [System.Uri]$Url
    $path = $uri.PathAndQuery
    $messageToSign = "$Method`n$path`n$timestamp"
    
    if ($Body) {
        $messageToSign += "`n$Body"
    }
    
    Write-Host "Message to sign:" -ForegroundColor Yellow
    Write-Host $messageToSign -ForegroundColor Gray
    Write-Host ""
    
    # Calculate HMAC signature
    $signature = Get-HmacSignature -message $messageToSign -secret $SharedSecret
    
    # Create Authorization header: SIF_HMACSHA256 <sessionToken>:<signature>
    $authHeader = "SIF_HMACSHA256 ${SessionToken}:${signature}"
    
    Write-Host "Authorization header:" -ForegroundColor Yellow
    Write-Host $authHeader -ForegroundColor Gray
    Write-Host ""
    
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
        
        Write-Host "Response:" -ForegroundColor Green
        $response | ConvertTo-Json -Depth 10
        return $response
    }
    catch {
        Write-Host "Error:" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd()
            Write-Host $responseBody -ForegroundColor Red
        }
        return $null
    }
}

# Test 1: List all environments (unauthenticated - should work via POST /environments exception)
Write-Host "Test 1: List all environments (GET /environments)" -ForegroundColor Cyan
Write-Host "Note: This requires authentication" -ForegroundColor Yellow
Write-Host ""

$environments = Invoke-AuthenticatedRequest `
    -Method "GET" `
    -Url "$envServiceUrl/environments" `
    -SessionToken $sessionToken `
    -SharedSecret $sharedSecret

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test 2: Get ADMIN environment details
if ($environments -and $environments.content) {
    $adminEnv = $environments.content | Where-Object { $_.type -eq "ADMIN" } | Select-Object -First 1
    
    if ($adminEnv) {
        Write-Host "Test 2: Get ADMIN environment by ID" -ForegroundColor Cyan
        Write-Host "ADMIN Environment ID: $($adminEnv.id)" -ForegroundColor Yellow
        Write-Host ""
        
        $envDetails = Invoke-AuthenticatedRequest `
            -Method "GET" `
            -Url "$envServiceUrl/environments/$($adminEnv.id)" `
            -SessionToken $sessionToken `
            -SharedSecret $sharedSecret
        
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
    }
}

# Test 3: Create a new DIRECT environment
Write-Host "Test 3: Create a new DIRECT environment" -ForegroundColor Cyan
Write-Host ""

$newEnvBody = @{
    applicationName = "TestApp"
    type = "DIRECT"
    sessionToken = "test-session-token-$(Get-Random)"
    sharedSecret = "test-shared-secret-$(Get-Random)"
    defaultZoneId = "default"
} | ConvertTo-Json

Write-Host "Request body:" -ForegroundColor Yellow
Write-Host $newEnvBody -ForegroundColor Gray
Write-Host ""

$newEnv = Invoke-AuthenticatedRequest `
    -Method "POST" `
    -Url "$envServiceUrl/environments" `
    -SessionToken $sessionToken `
    -SharedSecret $sharedSecret `
    -Body $newEnvBody

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test 4: Via Gateway
Write-Host "Test 4: List environments via Gateway" -ForegroundColor Cyan
Write-Host ""

$gatewayEnvs = Invoke-AuthenticatedRequest `
    -Method "GET" `
    -Url "$gatewayUrl/environments" `
    -SessionToken $sessionToken `
    -SharedSecret $sharedSecret

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Tests Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
