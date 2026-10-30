#!/bin/bash
# Build script for events-connector and its dependencies only
# Skips: job-service, requests-connector, responses-connector, integration-tests

echo "Building events-connector and required dependencies..."
echo "Skipping: job-service, requests-connector, responses-connector, integration-tests"
echo ""

mvn clean install \
  -pl sif-broker-common,environment-service,queue-service,subscription-service,gateway,events-connector,services-connector \
  "$@"
