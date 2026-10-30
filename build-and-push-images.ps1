# =============================================================================
# OSDB - Build All Docker Images and Push to ECR
# =============================================================================
# Usage:
#   .\build-and-push-images.ps1 -AccountId 123456789012 -Region us-east-1
#
# Options:
#   -AccountId   (required) Your AWS account ID (or set AWS_ACCOUNT_ID)
#   -Region      (required) AWS region where ECR repos live
#   -SkipPush    (optional) Build images locally without pushing to ECR
#   -Profile     (optional) AWS CLI profile (default: $env:AWS_PROFILE, else "default")
#   -Services    (optional) Comma-separated subset of services to build/push/redeploy.
#                When omitted, all services are processed.
#
# Examples:
#   # Build and push all images
#   .\build-and-push-images.ps1 -AccountId 123456789012 -Region us-east-1
#
#   # Build only (no push) - useful for local testing
#   .\build-and-push-images.ps1 -AccountId 123456789012 -Region us-east-1 -SkipPush
#
#   # Use a specific AWS profile
#   .\build-and-push-images.ps1 -AccountId 123456789012 -Region us-east-1 -Profile my-profile
#
#   # Build/push/redeploy only specific services
#   .\build-and-push-images.ps1 -AccountId 123456789012 -Region us-east-1 -Services gateway,environment-service
# =============================================================================

param(
    [string]$AccountId = $env:AWS_ACCOUNT_ID,

    [string]$Region = $(if ($env:AWS_REGION) { $env:AWS_REGION } else { "us-east-1" }),

    [switch]$SkipPush,

    [string]$Profile = $(if ($env:AWS_PROFILE) { $env:AWS_PROFILE } else { "default" }),

    [string]$RepoPrefix = $(if ($env:ECR_REPO_PREFIX) { $env:ECR_REPO_PREFIX } else { "osdb" }),

    [string]$ClusterName = $(if ($env:ECS_CLUSTER_NAME) { $env:ECS_CLUSTER_NAME } else { "osdb-cluster" }),

    # Optional subset of services to build/push/redeploy. When omitted, all
    # services are processed. Accepts a comma-separated list, e.g.
    #   -Services gateway,environment-service
    [string[]]$Services = @()
)

$ErrorActionPreference = "Continue"

# Required configuration. Set these in aws.env (see aws.env.example) or pass
# them explicitly. Failing here is deliberate: without an account ID the AWS
# CLI would otherwise act against whatever account happens to be configured.
if (-not $AccountId) {
    Write-Host "ERROR: AWS_ACCOUNT_ID is not set. Copy aws.env.example to aws.env and fill it in, or pass -AccountId." -ForegroundColor Red
    exit 1
}

# Pre-flight: Validate AWS session is active
Write-Host "Checking AWS session for profile '$Profile'..." -ForegroundColor Cyan
$env:AWS_PROFILE = $Profile
aws sts get-caller-identity --profile $Profile 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "AWS session expired or not logged in. Running 'aws sso login --profile $Profile'..." -ForegroundColor Yellow
    aws sso login --profile $Profile
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: SSO login failed. Cannot proceed." -ForegroundColor Red
        exit 1
    }
    # Verify again after login
    aws sts get-caller-identity --profile $Profile 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Still unable to authenticate after SSO login." -ForegroundColor Red
        exit 1
    }
}
Write-Host "AWS session valid." -ForegroundColor Green
Write-Host ""

# All services to build
$allServices = @(
    "gateway",
    "environment-service",
    "queue-service",
    "subscription-service",
    "job-service",
    "events-connector",
    "requests-connector",
    "responses-connector",
    "services-connector"
)

# If the caller passed an explicit -Services list, validate and use it;
# otherwise default to all services.
if ($Services.Count -gt 0) {
    $unknown = $Services | Where-Object { $allServices -notcontains $_ }
    if ($unknown.Count -gt 0) {
        Write-Host "ERROR: Unknown service(s): $($unknown -join ', ')" -ForegroundColor Red
        Write-Host "Valid services: $($allServices -join ', ')" -ForegroundColor Yellow
        exit 1
    }
    $services = $Services
} else {
    $services = $allServices
}

$ecrBase = "$AccountId.dkr.ecr.$Region.amazonaws.com"

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " OSDB Docker Build & Push" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Account:  $AccountId"
Write-Host " Region:   $Region"
Write-Host " Profile:  $Profile"
Write-Host " Repo:     $RepoPrefix"
Write-Host " ECR Base: $ecrBase"
Write-Host " Push:     $(-not $SkipPush)"
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build JARs with Maven. When a subset was requested, build only those
# modules (plus their dependencies via -am); otherwise build everything.
Write-Host "[1/4] Building JARs with Maven..." -ForegroundColor Yellow
$mvnStart = Get-Date
if ($Services.Count -gt 0) {
    $moduleList = ($services | ForEach-Object { ":$_" }) -join ","
    Write-Host "  Building selected modules: $($services -join ', ')" -ForegroundColor DarkGray
    mvn clean package -DskipTests -f pom.xml -pl $moduleList -am
} else {
    mvn clean package -DskipTests -f pom.xml
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Maven build failed!" -ForegroundColor Red
    exit 1
}
$mvnDuration = (Get-Date) - $mvnStart
Write-Host "Maven build completed in $($mvnDuration.TotalSeconds.ToString('F1'))s" -ForegroundColor Green
Write-Host ""

# Step 1b: Build extension-campusapi (not in parent reactor) — only when the optional
# module is present in this checkout and requests-connector is being built.
if ($services -contains "requests-connector" -and (Test-Path "extension-campusapi/pom.xml")) {
    Write-Host "[1b/4] Building extension-campusapi..." -ForegroundColor Yellow
    mvn package -DskipTests -f extension-campusapi/pom.xml
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: extension-campusapi build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "extension-campusapi built" -ForegroundColor Green

    # Copy extension JAR into requests-connector/target/extensions/ for Docker build
    New-Item -ItemType Directory -Force -Path "requests-connector/target/extensions" | Out-Null
    Copy-Item "extension-campusapi/target/extension-campusapi-*.jar" "requests-connector/target/extensions/"
    Write-Host "  Copied extension JAR to requests-connector/target/extensions/" -ForegroundColor Green
    Write-Host ""
}

# Step 2: Build Docker images
Write-Host "[2/4] Building Docker images..." -ForegroundColor Yellow

# Ensure extensions directory exists for requests-connector Docker build
# (in case step 1b was skipped or no extensions were built)
New-Item -ItemType Directory -Force -Path "requests-connector/target/extensions" | Out-Null

foreach ($service in $services) {
    Write-Host "  Building ${RepoPrefix}/${service}..." -NoNewline
    $proc = Start-Process -FilePath "docker" -ArgumentList "build","-t","${RepoPrefix}/${service}:latest","-t","${ecrBase}/${RepoPrefix}/${service}:latest","./$service" -Wait -PassThru -NoNewWindow -RedirectStandardError "NUL"
    if ($proc.ExitCode -ne 0) {
        Write-Host " FAILED (exit code $($proc.ExitCode))" -ForegroundColor Red
        Write-Host "  Re-running with output:"
        docker build -t "${RepoPrefix}/${service}:latest" -t "${ecrBase}/${RepoPrefix}/${service}:latest" "./$service"
        exit 1
    }
    Write-Host " OK" -ForegroundColor Green
}
Write-Host ""

# Step 3: Push to ECR (unless -SkipPush)
if (-not $SkipPush) {
    Write-Host "[3/4] Pushing images to ECR..." -ForegroundColor Yellow

    # Authenticate Docker to ECR
    Write-Host "  Authenticating to ECR..."
    $loginPassword = aws ecr get-login-password --region $Region --profile $Profile
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: ECR authentication failed! Check your AWS profile." -ForegroundColor Red
        exit 1
    }
    $loginPassword | docker login --username AWS --password-stdin $ecrBase
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker login to ECR failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Authenticated." -ForegroundColor Green

    # Push each image
    foreach ($service in $services) {
        Write-Host "  Pushing ${RepoPrefix}/${service}..." -NoNewline
        $proc = Start-Process -FilePath "docker" -ArgumentList "push","${ecrBase}/${RepoPrefix}/${service}:latest" -Wait -PassThru -NoNewWindow -RedirectStandardError "NUL"
        if ($proc.ExitCode -ne 0) {
            Write-Host " FAILED" -ForegroundColor Red
            docker push "${ecrBase}/${RepoPrefix}/${service}:latest"
            exit 1
        }
        Write-Host " OK" -ForegroundColor Green
    }

    # Step 4: Force ECS services to pull new images (only services we built)
    Write-Host ""
    Write-Host "[4/4] Forcing ECS redeployment (built services only)..." -ForegroundColor Yellow
    $clusterName = $ClusterName
    $serviceArns = aws ecs list-services --cluster $clusterName --profile $Profile --region $Region --output json 2>$null | ConvertFrom-Json
    if ($serviceArns -and $serviceArns.serviceArns) {
        foreach ($arn in $serviceArns.serviceArns) {
            $svcName = $arn.Split("/")[-1]
            # Only redeploy services that match what we built
            $shouldRedeploy = $false
            foreach ($built in $services) {
                if ($svcName -like "*$built*") {
                    $shouldRedeploy = $true
                    break
                }
            }
            if (-not $shouldRedeploy) {
                Write-Host "  Skipping $svcName (not in build list)" -ForegroundColor DarkGray
                continue
            }
            Write-Host "  Redeploying $svcName..." -NoNewline
            aws ecs update-service --cluster $clusterName --service $arn --force-new-deployment --profile $Profile --region $Region --output text 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host " OK" -ForegroundColor Green
            } else {
                Write-Host " FAILED" -ForegroundColor Red
            }
        }
    } else {
        Write-Host "  No services found in cluster $clusterName" -ForegroundColor Yellow
    }
} else {
    Write-Host "[3/4] Skipping push (-SkipPush flag set)" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Done!" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

if (-not $SkipPush) {
    Write-Host "Images are available at:"
    foreach ($service in $services) {
        Write-Host "  ${ecrBase}/${RepoPrefix}/${service}:latest"
    }
} else {
    Write-Host "Local images built:"
    foreach ($service in $services) {
        Write-Host "  ${RepoPrefix}/${service}:latest"
    }
}
Write-Host ""
