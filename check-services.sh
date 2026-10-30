#!/bin/bash
# SIF Unity Broker - Health Check Script (Bash)
# This script checks the health status of all broker services

echo "========================================"
echo "SIF Unity Broker - Health Check"
echo "========================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to check service health
check_service() {
    local service_name=$1
    local port=$2
    local url="http://localhost:${port}/actuator/health"
    
    printf "%-25s " "$service_name"
    
    # Try to get health status
    response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "$url" 2>/dev/null)
    
    if [ "$response" = "200" ]; then
        # Get detailed status
        status=$(curl -s "$url" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        if [ "$status" = "UP" ]; then
            echo -e "${GREEN}✓ UP${NC}       http://localhost:${port}"
        else
            echo -e "${YELLOW}⚠ ${status}${NC}    http://localhost:${port}"
        fi
    elif [ "$response" = "503" ]; then
        echo -e "${YELLOW}⚠ DOWN${NC}     http://localhost:${port}"
    else
        echo -e "${RED}✗ OFFLINE${NC}  http://localhost:${port}"
    fi
}

# Check infrastructure
echo -e "${CYAN}Infrastructure:${NC}"
echo ""

# Check PostgreSQL
printf "%-25s " "PostgreSQL"
if psql -U sif_broker -d sif_broker -h localhost -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ UP${NC}       localhost:5432"
else
    echo -e "${RED}✗ OFFLINE${NC}  localhost:5432"
fi

# Check Redis
printf "%-25s " "Redis"
if redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ UP${NC}       localhost:6379"
else
    echo -e "${RED}✗ OFFLINE${NC}  localhost:6379"
fi

echo ""
echo -e "${CYAN}Services:${NC}"
echo ""

# Check all services
check_service "Gateway" 8080
check_service "Environment Service" 8081
check_service "Queue Service" 8082
check_service "Subscription Service" 8083
check_service "Job Service" 8084
check_service "Requests Connector" 8085
check_service "Responses Connector" 8086
check_service "Events Connector" 8087
check_service "Services Connector" 8088

echo ""
echo "========================================"

# Count services
up_count=0
down_count=0

for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
    response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "http://localhost:${port}/actuator/health" 2>/dev/null)
    if [ "$response" = "200" ]; then
        up_count=$((up_count + 1))
    else
        down_count=$((down_count + 1))
    fi
done

echo -e "Status: ${GREEN}${up_count} UP${NC} / ${RED}${down_count} DOWN${NC} / 9 Total"
echo "========================================"
echo ""

# Exit with error if any service is down
if [ $down_count -gt 0 ]; then
    exit 1
fi

exit 0
