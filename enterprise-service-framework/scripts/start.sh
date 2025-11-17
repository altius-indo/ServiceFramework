#!/bin/bash

###############################################################################
# Enterprise Service Framework - Startup Script
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_COMPOSE_FILE="$PROJECT_DIR/docker/docker-compose.yml"

# PID file
PID_FILE="$PROJECT_DIR/.service.pid"
LOG_FILE="$PROJECT_DIR/logs/service.log"

# Functions
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed. Please install JDK 17+"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        print_error "Java 17+ is required. Found version: $JAVA_VERSION"
        exit 1
    fi
    print_success "Java version: $JAVA_VERSION"
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker"
        exit 1
    fi
    print_success "Docker is installed"
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
    print_success "Docker Compose is available"
    
    # Check if Docker daemon is running
    if ! docker info &> /dev/null; then
        print_error "Docker daemon is not running. Please start Docker"
        exit 1
    fi
    print_success "Docker daemon is running"
}

start_dependencies() {
    print_info "Starting dependencies (Redis, DynamoDB)..."
    
    cd "$PROJECT_DIR"
    
    # Check if services are already running
    if docker-compose -f "$DOCKER_COMPOSE_FILE" ps | grep -q "Up"; then
        print_warning "Some services are already running"
        docker-compose -f "$DOCKER_COMPOSE_FILE" ps
    else
        # Start services
        docker-compose -f "$DOCKER_COMPOSE_FILE" up -d
        
        # Wait for services to be healthy
        print_info "Waiting for services to be healthy..."
        local max_attempts=30
        local attempt=0
        
        while [ $attempt -lt $max_attempts ]; do
            if docker-compose -f "$DOCKER_COMPOSE_FILE" ps | grep -q "healthy"; then
                print_success "All services are healthy"
                break
            fi
            attempt=$((attempt + 1))
            sleep 2
        done
        
        if [ $attempt -eq $max_attempts ]; then
            print_error "Services did not become healthy in time"
            docker-compose -f "$DOCKER_COMPOSE_FILE" logs
            exit 1
        fi
    fi
    
    print_success "Dependencies started successfully"
}

build_application() {
    print_info "Building application..."
    
    cd "$PROJECT_DIR"
    
    if [ ! -f "./gradlew" ]; then
        print_error "Gradle wrapper not found"
        exit 1
    fi
    
    chmod +x ./gradlew
    ./gradlew clean build -x test
    
    print_success "Application built successfully"
}

start_application() {
    print_info "Starting application..."
    
    cd "$PROJECT_DIR"
    
    # Create logs directory
    mkdir -p "$(dirname "$LOG_FILE")"
    
    # Check if already running
    if [ -f "$PID_FILE" ]; then
        OLD_PID=$(cat "$PID_FILE")
        if ps -p "$OLD_PID" > /dev/null 2>&1; then
            print_warning "Application is already running (PID: $OLD_PID)"
            print_info "Use './scripts/stop.sh' to stop it first"
            exit 1
        else
            # Remove stale PID file
            rm -f "$PID_FILE"
        fi
    fi
    
    # Start application in background
    nohup ./gradlew run > "$LOG_FILE" 2>&1 &
    APP_PID=$!
    
    # Save PID
    echo "$APP_PID" > "$PID_FILE"
    
    print_success "Application started (PID: $APP_PID)"
    print_info "Logs: $LOG_FILE"
    
    # Wait a bit and check if still running
    sleep 3
    if ! ps -p "$APP_PID" > /dev/null 2>&1; then
        print_error "Application failed to start. Check logs: $LOG_FILE"
        tail -20 "$LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
    
    # Wait for application to be ready
    print_info "Waiting for application to be ready..."
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
            print_success "Application is ready!"
            break
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    
    if [ $attempt -eq $max_attempts ]; then
        print_warning "Application may not be fully ready. Check logs: $LOG_FILE"
    fi
}

show_status() {
    print_info "Service Status:"
    echo ""
    
    # Docker services
    echo "Docker Services:"
    docker-compose -f "$DOCKER_COMPOSE_FILE" ps
    echo ""
    
    # Application
    if [ -f "$PID_FILE" ]; then
        APP_PID=$(cat "$PID_FILE")
        if ps -p "$APP_PID" > /dev/null 2>&1; then
            echo "Application: Running (PID: $APP_PID)"
        else
            echo "Application: Not running (stale PID file)"
        fi
    else
        echo "Application: Not running"
    fi
    echo ""
    
    # Health check
    if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
        echo "Health Check: OK"
        curl -s http://localhost:8080/health | jq '.' 2>/dev/null || curl -s http://localhost:8080/health
    else
        echo "Health Check: Failed"
    fi
}

# Main execution
main() {
    print_info "=========================================="
    print_info "Enterprise Service Framework - Startup"
    print_info "=========================================="
    echo ""
    
    check_prerequisites
    echo ""
    
    start_dependencies
    echo ""
    
    build_application
    echo ""
    
    start_application
    echo ""
    
    show_status
    echo ""
    
    print_success "=========================================="
    print_success "Service started successfully!"
    print_success "=========================================="
    print_info "Application URL: http://localhost:8080"
    print_info "Health Check: http://localhost:8080/health"
    print_info "Logs: $LOG_FILE"
    print_info "PID: $APP_PID (saved in $PID_FILE)"
    print_info ""
    print_info "To stop the service, run: ./scripts/stop.sh"
}

# Run main function
main "$@"

