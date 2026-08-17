# Makefile for Environment Service
# Provides convenient commands for building, running, and managing the service

.PHONY: help build start stop restart logs clean test db-shell health dev prod

# Default target
help:
	@echo "Environment Service - Available Commands:"
	@echo ""
	@echo "  make build       - Build the Spring Boot JAR"
	@echo "  make start       - Start services (PostgreSQL + Environment Service)"
	@echo "  make stop        - Stop all services"
	@echo "  make restart     - Restart all services"
	@echo "  make logs        - View logs from all services"
	@echo "  make clean       - Stop services and remove volumes (deletes data)"
	@echo "  make test        - Run all tests"
	@echo "  make db-shell    - Open PostgreSQL shell"
	@echo "  make health      - Check service health"
	@echo "  make dev         - Start in development mode (with debugging)"
	@echo "  make prod        - Build and start in production mode"
	@echo ""

# Build the Spring Boot application
build:
	@echo "Building Spring Boot application..."
	./mvnw clean package -DskipTests
	@echo "Build complete!"

# Build with tests
build-test:
	@echo "Building Spring Boot application with tests..."
	./mvnw clean package
	@echo "Build complete!"

# Start services in production mode
start: build
	@echo "Starting services..."
	docker-compose up -d
	@echo "Services started!"
	@echo "Environment Service: http://localhost:8081"
	@echo "PostgreSQL: localhost:5432"

# Start services in development mode
dev: build
	@echo "Starting services in development mode..."
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d
	@echo "Services started in development mode!"
	@echo "Environment Service: http://localhost:8081"
	@echo "Debug Port: localhost:5005"
	@echo "PostgreSQL: localhost:5432"

# Stop services
stop:
	@echo "Stopping services..."
	docker-compose down
	@echo "Services stopped!"

# Restart services
restart: stop start

# View logs
logs:
	docker-compose logs -f

# View logs for specific service
logs-app:
	docker-compose logs -f environment-service

logs-db:
	docker-compose logs -f postgres

# Clean up everything (including volumes)
clean:
	@echo "Stopping services and removing volumes..."
	docker-compose down -v
	@echo "Cleanup complete!"

# Run tests
test:
	@echo "Running tests..."
	./mvnw test

# Run integration tests
test-integration:
	@echo "Running integration tests..."
	./mvnw verify

# Open PostgreSQL shell
db-shell:
	@echo "Opening PostgreSQL shell..."
	docker-compose exec postgres psql -U sif_broker -d sif_broker

# Check service health
health:
	@echo "Checking service health..."
	@curl -s http://localhost:8081/actuator/health | jq '.' || echo "Service not responding"

# Check service status
status:
	@echo "Service status:"
	@docker-compose ps

# Build Docker image only
docker-build: build
	@echo "Building Docker image..."
	docker-compose build
	@echo "Docker image built!"

# Production build and start
prod: build docker-build
	@echo "Starting in production mode..."
	docker-compose up -d
	@echo "Production deployment complete!"

# Database backup
db-backup:
	@echo "Backing up database..."
	@mkdir -p backups
	@docker-compose exec -T postgres pg_dump -U sif_broker sif_broker > backups/backup_$$(date +%Y%m%d_%H%M%S).sql
	@echo "Database backed up to backups/"

# Database restore (usage: make db-restore FILE=backups/backup.sql)
db-restore:
	@echo "Restoring database from $(FILE)..."
	@docker-compose exec -T postgres psql -U sif_broker -d sif_broker < $(FILE)
	@echo "Database restored!"

# Reset database (drops all data and re-runs migrations)
db-reset:
	@echo "Resetting database..."
	docker-compose down -v
	docker-compose up -d
	@echo "Database reset complete!"

# Show environment variables
env:
	@echo "Current environment configuration:"
	@docker-compose config

# Tail logs with grep filter (usage: make grep PATTERN="ERROR")
grep:
	docker-compose logs -f | grep "$(PATTERN)"

# Quick test - register a test environment
test-register:
	@echo "Registering test environment..."
	@curl -X POST http://localhost:8081/environments \
		-H "Content-Type: application/json" \
		-d '{"applicationName":"TestApp","type":"CONSUMER","defaultZoneId":"default"}' \
		| jq '.'

# Show metrics
metrics:
	@echo "Service metrics:"
	@curl -s http://localhost:8081/actuator/metrics | jq '.'

# Show info
info:
	@echo "Service info:"
	@curl -s http://localhost:8081/actuator/info | jq '.'
