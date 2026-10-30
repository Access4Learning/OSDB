# Temporary script to run ForeignKeyConstraintIntegrationTest
# by temporarily moving broken test files

Write-Host "Temporarily moving broken test files..." -ForegroundColor Yellow

$brokenTestDirs = @(
    "environment-service/src/test/java/org/sifunity/broker/environment/secrets",
    "environment-service/src/test/java/org/sifunity/broker/environment/controller"
)

$tempDir = "temp-broken-tests"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

foreach ($dir in $brokenTestDirs) {
    if (Test-Path $dir) {
        $dirName = Split-Path $dir -Leaf
        Move-Item -Path $dir -Destination "$tempDir/$dirName" -Force
        Write-Host "  Moved: $dir" -ForegroundColor Gray
    }
}

Write-Host "`nRunning ForeignKeyConstraintIntegrationTest..." -ForegroundColor Cyan

try {
    mvn test "-Dtest=ForeignKeyConstraintIntegrationTest" "-pl" "environment-service"
    $exitCode = $LASTEXITCODE
} finally {
    Write-Host "`nRestoring broken test files..." -ForegroundColor Yellow
    
    foreach ($dir in $brokenTestDirs) {
        $dirName = Split-Path $dir -Leaf
        $tempPath = "$tempDir/$dirName"
        if (Test-Path $tempPath) {
            Move-Item -Path $tempPath -Destination $dir -Force
            Write-Host "  Restored: $dir" -ForegroundColor Gray
        }
    }
    
    Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "`nDone!" -ForegroundColor Green
exit $exitCode
