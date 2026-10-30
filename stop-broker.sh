#!/bin/bash
# SIF Unity Broker - Stop Script (Bash)
# This script stops all running broker services

echo "========================================"
echo "SIF Unity Broker - Stopping Services"
echo "========================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Check if logs directory exists
if [ ! -d "$SCRIPT_DIR/logs" ]; then
    echo -e "${YELLOW}No logs directory found. Services may not be running.${NC}"
    exit 0
fi

# Function to stop a service
stop_service() {
    local service_name=$1
    local pid_file="$SCRIPT_DIR/logs/${service_name}.pid"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            echo -e "${YELLOW}Stopping ${service_name}...${NC}"
            kill $pid
            
            # Wait for process to stop (max 10 seconds)
            local count=0
            while ps -p $pid > /dev/null 2>&1 && [ $count -lt 10 ]; do
                sleep 1
                count=$((count + 1))
            done
            
            # Force kill if still running
            if ps -p $pid > /dev/null 2>&1; then
                echo -e "${RED}  Force stopping ${service_name}...${NC}"
                kill -9 $pid
            fi
            
            echo -e "${GREEN}✓ ${service_name} stopped${NC}"
        else
            echo -e "${YELLOW}${service_name} is not running (PID $pid not found)${NC}"
        fi
        rm -f "$pid_file"
    else
        echo -e "${YELLOW}${service_name} PID file not found${NC}"
    fi
}

# Stop services in reverse order (Gateway first, Environment Service last)
echo -e "${CYAN}Stopping services...${NC}"
echo ""

stop_service "gateway"
stop_service "services-connector"
stop_service "events-connector"
stop_service "responses-connector"
stop_service "requests-connector"
stop_service "job-service"
stop_service "subscription-service"
stop_service "queue-service"
stop_service "environment-service"

echo ""
echo "========================================"
echo -e "${GREEN}All services stopped${NC}"
echo "========================================"
echo ""

# Optional: Clean up log files
read -p "Delete log files? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm -rf "$SCRIPT_DIR/logs"
    echo -e "${GREEN}✓ Log files deleted${NC}"
fi

echo ""
