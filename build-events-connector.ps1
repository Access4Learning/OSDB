# Build script for events-connector and its dependencies only
# Skips: job-service, requests-connector, responses-connector, integration-tests

Write-Host "Building events-connector and required dependencies..." -ForegroundColor Green
Write-Host "Skipping: job-service, requests-connector, responses-connector, integration-tests" -ForegroundColor Yellow
Write-Host "Skipping tests to avoid integration test issues" -ForegroundColor Yellow
Write-Host ""

mvn clean install `
  -pl sif-broker-common,environment-service,queue-service,subscription-service,gateway,events-connector,services-connector `
  -DskipTests `
  $args
