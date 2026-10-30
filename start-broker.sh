#!/bin/bash
# SIF Unity Broker - Local Startup Script (Bash)
# This script starts all broker services in the correct order

echo "========================================"
echo "SIF Unity Broker - Local Startup"
echo "========================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Build all modules first so sif-broker-common (and every service) is installed
# to the local .m2 repository. Services resolve sif-broker-common as a dependency,
# so on a fresh clone the spring-boot:run steps fail without this.
echo -e "${YELLOW}Building all modules (mvn install -DskipTests)...${NC}"
if ! mvn install -DskipTests -q; then
    echo -e "${RED}✗ Build failed! Check errors above.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Build complete${NC}"
echo ""

# Check if PostgreSQL is running
echo -e "${YELLOW}Checking PostgreSQL...${NC}"
if psql -U postgres -d sif_broker -h localhost -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL is running${NC}"
else
    echo -e "${RED}✗ PostgreSQL connection failed${NC}"
    echo -e "${RED}  Please start PostgreSQL and ensure database 'sif_broker' exists (user 'postgres')${NC}"
    exit 1
fi

# Check if Redis is running
echo -e "${YELLOW}Checking Redis...${NC}"
if redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis is running${NC}"
else
    echo -e "${RED}✗ Redis connection failed${NC}"
    echo -e "${RED}  Please start Redis${NC}"
    exit 1
fi

echo ""
echo -e "${CYAN}Starting services...${NC}"
echo ""

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Create logs directory
mkdir -p "$SCRIPT_DIR/logs"

# 1. Start Environment Service (FIRST - required by all others)
echo -e "${YELLOW}[1/9] Starting Environment Service (port 8081)...${NC}"
cd "$SCRIPT_DIR/environment-service"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/environment-service.log" 2>&1 &
ENV_PID=$!
echo $ENV_PID > "$SCRIPT_DIR/logs/environment-service.pid"
cd "$SCRIPT_DIR"
sleep 5

# Wait for Environment Service to be ready
echo -e "${YELLOW}Waiting for Environment Service to start...${NC}"
max_attempts=30
attempt=0
env_ready=false

while [ $attempt -lt $max_attempts ] && [ "$env_ready" = false ]; do
    if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
        env_ready=true
        echo -e "${GREEN}✓ Environment Service is ready${NC}"
    else
        attempt=$((attempt + 1))
        sleep 2
    fi
done

if [ "$env_ready" = false ]; then
    echo -e "${RED}✗ Environment Service failed to start${NC}"
    echo -e "${RED}  Check logs/environment-service.log for errors${NC}"
    exit 1
fi

echo ""

# 2. Start Queue Service
echo -e "${YELLOW}[2/9] Starting Queue Service (port 8082)...${NC}"
cd "$SCRIPT_DIR/queue-service"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/queue-service.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/queue-service.pid"
cd "$SCRIPT_DIR"
sleep 3

# 3. Start Subscription Service
echo -e "${YELLOW}[3/9] Starting Subscription Service (port 8083)...${NC}"
cd "$SCRIPT_DIR/subscription-service"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/subscription-service.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/subscription-service.pid"
cd "$SCRIPT_DIR"
sleep 3

# 4. Start Job Service
echo -e "${YELLOW}[4/9] Starting Job Service (port 8086)...${NC}"
cd "$SCRIPT_DIR/job-service"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/job-service.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/job-service.pid"
cd "$SCRIPT_DIR"
sleep 3

echo ""
echo -e "${YELLOW}Waiting for core services to initialize...${NC}"
sleep 15

# 5. Start Requests Connector
echo -e "${YELLOW}[5/9] Starting Requests Connector (port 8085)...${NC}"
cd "$SCRIPT_DIR/requests-connector"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/requests-connector.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/requests-connector.pid"
cd "$SCRIPT_DIR"
sleep 3

# 6. Start Responses Connector
echo -e "${YELLOW}[6/9] Starting Responses Connector (port 8088)...${NC}"
cd "$SCRIPT_DIR/responses-connector"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/responses-connector.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/responses-connector.pid"
cd "$SCRIPT_DIR"
sleep 3

# 7. Start Events Connector
echo -e "${YELLOW}[7/9] Starting Events Connector (port 8084)...${NC}"
cd "$SCRIPT_DIR/events-connector"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/events-connector.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/events-connector.pid"
cd "$SCRIPT_DIR"
sleep 3

# 8. Start Services Connector
echo -e "${YELLOW}[8/9] Starting Services Connector (port 8087)...${NC}"
cd "$SCRIPT_DIR/services-connector"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/services-connector.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/services-connector.pid"
cd "$SCRIPT_DIR"
sleep 3

echo ""
echo -e "${YELLOW}Waiting for connectors to initialize...${NC}"
sleep 15

# 9. Start Gateway (LAST - routes to all other services)
echo -e "${YELLOW}[9/9] Starting Gateway (port 8080)...${NC}"
cd "$SCRIPT_DIR/gateway"
mvn spring-boot:run -Dspring-boot.run.profiles=local > "$SCRIPT_DIR/logs/gateway.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/gateway.pid"
cd "$SCRIPT_DIR"

echo ""
echo "========================================"
echo -e "${GREEN}All services are starting!${NC}"
echo "========================================"
echo ""
echo -e "${YELLOW}Logs:${NC}"
echo "  All service logs are in: logs/"
echo "  Tail a log: tail -f logs/gateway.log"
echo ""
echo -e "${YELLOW}Health Checks (wait ~60 seconds for all services):${NC}"
echo "  Gateway:              http://localhost:8080/actuator/health"
echo "  Environment Service:  http://localhost:8081/actuator/health"
echo "  Queue Service:        http://localhost:8082/actuator/health"
echo "  Subscription Service: http://localhost:8083/actuator/health"
echo "  Job Service:          http://localhost:8086/actuator/health"
echo "  Requests Connector:   http://localhost:8085/actuator/health"
echo "  Responses Connector:  http://localhost:8088/actuator/health"
echo "  Events Connector:     http://localhost:8084/actuator/health"
echo "  Services Connector:   http://localhost:8087/actuator/health"
echo ""
echo -e "${YELLOW}Admin Credentials (HMAC-SHA256):${NC}"
echo "  Session Token:  admin-bootstrap-token   (override with ADMIN_SESSION_TOKEN)"
echo "  Shared Secret:  admin-bootstrap-secret  (override with ADMIN_SHARED_SECRET)"
echo "  Auth is HMAC-signed (no username/password). See API-AUTHENTICATION-GUIDE.md."
echo ""
echo -e "${YELLOW}To stop all services:${NC}"
echo "  Run: ./stop-broker.sh"
echo ""
