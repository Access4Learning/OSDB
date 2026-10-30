param(
    [switch]$SkipInvalidation,

    # Deployment target. Set these in aws.env (see aws.env.example) or pass
    # them explicitly. The three without defaults identify resources in your
    # own account, so there is no sensible fallback.
    [string]$Region         = $(if ($env:AWS_REGION) { $env:AWS_REGION } else { "us-east-1" }),
    [string]$Profile        = $(if ($env:AWS_PROFILE) { $env:AWS_PROFILE } else { "default" }),
    [string]$BucketName     = $env:ADMIN_CONSOLE_BUCKET,
    [string]$DistributionId = $env:CLOUDFRONT_DISTRIBUTION_ID,
    [string]$GatewayUrl     = $env:GATEWAY_URL
)

$ErrorActionPreference = "Stop"

$AdminConsolePath = "experimental-admin-console"
$InvalidationPath = "/*"

$missing = @()
if (-not $BucketName)     { $missing += "ADMIN_CONSOLE_BUCKET" }
if (-not $DistributionId) { $missing += "CLOUDFRONT_DISTRIBUTION_ID" }
if (-not $GatewayUrl)     { $missing += "GATEWAY_URL" }
if ($missing.Count -gt 0) {
    Write-Host "ERROR: missing required configuration: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "Copy aws.env.example to aws.env and fill it in, or pass the matching parameters." -ForegroundColor Yellow
    exit 1
}

$Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    # Phase 1: Prerequisites Check
    Write-Host "Checking prerequisites..." -ForegroundColor Cyan

    if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: AWS CLI not found. Install it from https://aws.amazon.com/cli/" -ForegroundColor Red
        exit 1
    }

    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: npm not found. Install Node.js from https://nodejs.org/" -ForegroundColor Red
        exit 1
    }

    if (-not (Test-Path "$AdminConsolePath/node_modules")) {
        Write-Host "ERROR: node_modules not found. Run 'npm install' in $AdminConsolePath/" -ForegroundColor Red
        exit 1
    }

    # Phase 2: Build
    Write-Host "Building admin console..." -ForegroundColor Cyan
    $env:VITE_GATEWAY_URL = $GatewayUrl

    Push-Location $AdminConsolePath
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Build failed" -ForegroundColor Red
            exit 1
        }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path "$AdminConsolePath/dist/index.html")) {
        Write-Host "ERROR: Build output missing - expected $AdminConsolePath/dist/index.html" -ForegroundColor Red
        exit 1
    }

    # Phase 3: S3 Sync
    Write-Host "Uploading to S3 bucket $BucketName..." -ForegroundColor Cyan
    aws s3 sync "./$AdminConsolePath/dist" "s3://$BucketName" --delete --profile $Profile --region $Region
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: S3 sync failed" -ForegroundColor Red
        exit 1
    }

    # Phase 4: CloudFront Invalidation
    if ($SkipInvalidation) {
        Write-Host "Skipping CloudFront invalidation (-SkipInvalidation flag provided)" -ForegroundColor Yellow
    } else {
        Write-Host "Creating CloudFront invalidation..." -ForegroundColor Cyan
        $invalidationOutput = aws cloudfront create-invalidation --distribution-id $DistributionId --paths $InvalidationPath --profile $Profile 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "WARNING: CloudFront invalidation failed (files are already in S3)" -ForegroundColor Yellow
        } else {
            $invalidationId = ($invalidationOutput | ConvertFrom-Json).Invalidation.Id
            Write-Host "CloudFront invalidation created: $invalidationId" -ForegroundColor Green
        }
    }

    # Phase 5: Completion
    $Stopwatch.Stop()
    $elapsed = $Stopwatch.Elapsed
    Write-Host "`nDeployment complete! Admin console available at:" -ForegroundColor Green
    Write-Host "  $GatewayUrl/admin/" -ForegroundColor Green
    Write-Host "Elapsed time: $($elapsed.Minutes)m $($elapsed.Seconds)s" -ForegroundColor Cyan
    exit 0

} catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
    exit 1
}
