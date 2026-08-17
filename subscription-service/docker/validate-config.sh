#!/bin/bash
# Docker Configuration Validation Script for Subscription Service

set -e

echo "=== Docker Configuration Validation ==="
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed"
    exit 1
fi
echo "✅ Docker is installed: $(docker --version)"

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is not installed"
    exit 1
fi
echo "✅ Docker Compose is installed"

# Validate Dockerfile syntax
echo ""
echo "Validating Dockerfile..."
if docker build --no-cache --target build -t subscription-service:validate-build . > /dev/null 2>&1; then
    echo "✅ Dockerfile build stage is valid"
else
    echo "❌ Dockerfile build stage has errors"
    exit 1
fi

# Validate docker-compose.yml syntax
echo ""
echo "Validating docker-compose.yml..."
if docker-compose config > /dev/null 2>&1; then
    echo "✅ docker-compose.yml is valid"
else
    echo "❌ docker-compose.yml has syntax errors"
    exit 1
fi

# Check required ports are available
echo ""
echo "Checking port availability..."
PORTS=(8083 5433 6380 8082)
for PORT in "${PORTS[@]}"; do
    if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "⚠️  Port $PORT is already in use"
    else
        echo "✅ Port $PORT is available"
    fi
done

# Validate mockserver configuration
echo ""
echo "Validating mockserver configuration..."
if [ -f "docker/mockserver-init.json" ]; then
    if jq empty docker/mockserver-init.json 2>/dev/null; then
        echo "✅ mockserver-init.json is valid JSON"
    else
        echo "⚠️  mockserver-init.json may have JSON syntax issues (jq not available or file invalid)"
    fi
else
    echo "❌ mockserver-init.json not found"
    exit 1
fi

echo ""
echo "=== Validation Complete ==="
echo ""
echo "To build and run the services:"
echo "  docker-compose up -d"
echo ""
echo "To test the API:"
echo "  curl http://localhost:8083/actuator/health"
