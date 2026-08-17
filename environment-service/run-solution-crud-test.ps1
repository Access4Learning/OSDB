# PowerShell script to run SolutionCrudIntegrationTest
# This script runs the integration test for solution CRUD operations

Write-Host "Running SolutionCrudIntegrationTest..." -ForegroundColor Cyan
Write-Host ""

# Run the test
& .\mvnw.cmd test -Dtest=SolutionCrudIntegrationTest

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✓ SolutionCrudIntegrationTest passed successfully!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "✗ SolutionCrudIntegrationTest failed. See output above for details." -ForegroundColor Red
    exit 1
}
