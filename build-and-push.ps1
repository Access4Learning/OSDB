param(
    [string]$ServiceName = "",

    # AWS target. Set these in aws.env (see aws.env.example) or pass them
    # explicitly. AccountId has no default on purpose — without it the AWS CLI
    # would act against whatever account happens to be configured.
    [string]$AccountId  = $env:AWS_ACCOUNT_ID,
    [string]$Region     = $(if ($env:AWS_REGION) { $env:AWS_REGION } else { "us-east-1" }),
    [string]$RepoPrefix = $(if ($env:ECR_REPO_PREFIX) { $env:ECR_REPO_PREFIX } else { "osdb" })
)

$ErrorActionPreference = "Stop"

if (-not $AccountId) {
    Write-Host "ERROR: AWS_ACCOUNT_ID is not set. Copy aws.env.example to aws.env and fill it in, or pass -AccountId." -ForegroundColor Red
    exit 1
}

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Registry = "$AccountId.dkr.ecr.$Region.amazonaws.com"

$AllServices = @(
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

# Determine which services to build
if ($ServiceName -ne "") {
    if ($AllServices -notcontains $ServiceName) {
        Write-Host "ERROR: Unknown service '$ServiceName'. Valid services: $($AllServices -join ', ')" -ForegroundColor Red
        exit 1
    }
    $Services = @($ServiceName)
} else {
    $Services = $AllServices
}

try {
    # ECR authentication
    Write-Host "Authenticating with ECR in $Region..." -ForegroundColor Cyan
    $password = aws ecr get-login-password --region $Region
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to get ECR login password" -ForegroundColor Red
        exit 1
    }
    $password | docker login --username AWS --password-stdin "$Registry"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker login to ECR failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "ECR authentication successful." -ForegroundColor Green

    foreach ($service in $Services) {
        $imageBase = "$Registry/$RepoPrefix/$service"
        $latestTag = "${imageBase}:latest"
        $timestampTag = "${imageBase}:$Timestamp"

        # Maven build
        Write-Host "`nBuilding $service with Maven..." -ForegroundColor Cyan
        .\mvnw clean package -DskipTests "-pl" $service "-am"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Maven build failed for $service" -ForegroundColor Red
            exit 1
        }
        Write-Host "Maven build successful for $service." -ForegroundColor Green

        # Ensure target/extensions/ exists (required by some Dockerfiles for LOADER_PATH)
        $extensionsDir = "./$service/target/extensions"
        if (-not (Test-Path $extensionsDir)) {
            New-Item -ItemType Directory -Path $extensionsDir -Force | Out-Null
        }

        # For requests-connector: build and copy the extension jar, but only when
        # the optional extension-campusapi module is present in this checkout.
        if ($service -eq "requests-connector" -and (Test-Path "extension-campusapi/pom.xml")) {
            Write-Host "Building extension-campusapi..." -ForegroundColor Cyan
            .\mvnw package -DskipTests -f extension-campusapi/pom.xml
            if ($LASTEXITCODE -ne 0) {
                Write-Host "ERROR: extension-campusapi build failed" -ForegroundColor Red
                exit 1
            }
            Copy-Item "extension-campusapi/target/extension-campusapi-*.jar" $extensionsDir
            Write-Host "Copied extension-campusapi jar to $extensionsDir" -ForegroundColor Green
        }

        # Docker build
        Write-Host "Building Docker image for $service..." -ForegroundColor Cyan
        docker build -t $latestTag -t $timestampTag "./$service"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Docker build failed for $service" -ForegroundColor Red
            exit 1
        }
        Write-Host "Docker image built for $service." -ForegroundColor Green

        # Push latest tag
        Write-Host "Pushing $latestTag..." -ForegroundColor Cyan
        docker push $latestTag
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Failed to push $latestTag" -ForegroundColor Red
            exit 1
        }

        # Push timestamp tag
        Write-Host "Pushing $timestampTag..." -ForegroundColor Cyan
        docker push $timestampTag
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Failed to push $timestampTag" -ForegroundColor Red
            exit 1
        }

        Write-Host "Successfully pushed $service [$latestTag, $timestampTag]" -ForegroundColor Green
    }

    Write-Host "`nAll services built and pushed successfully." -ForegroundColor Green
    exit 0
} catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
    exit 1
}
