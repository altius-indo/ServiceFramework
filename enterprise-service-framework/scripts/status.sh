#!/bin/bash

###############################################################################
# Enterprise Service Framework - Status Script
###############################################################################

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

check_docker_services() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Docker Services"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        print_error "Docker Compose file not found: $DOCKER_COMPOSE_FILE"
        return
    fi
    
    cd "$PROJECT_DIR"
    docker-compose -f "$DOCKER_COMPOSE_FILE" ps
    echo ""
}

check_application() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Application"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [ -f "$PID_FILE" ]; then
        APP_PID=$(cat "$PID_FILE")
        if ps -p "$APP_PID" > /dev/null 2>&1; then
            print_success "Status: Running"
            echo "PID: $APP_PID"
            
            # Get process info
            if command -v ps &> /dev/null; then
                echo "Process Info:"
                ps -p "$APP_PID" -o pid,ppid,user,%cpu,%mem,etime,cmd | tail -n +2
            fi
        else
            print_warning "Status: Not running (stale PID file)"
            echo "PID file exists but process is not running"
        fi
    else
        print_warning "Status: Not running"
        echo "PID file not found"
    fi
    echo ""
}

check_health() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Health Check"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if command -v curl &> /dev/null; then
        if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
            print_success "Health endpoint is responding"
            echo ""
            echo "Response:"
            curl -s http://localhost:8080/health | jq '.' 2>/dev/null || curl -s http://localhost:8080/health
        else
            print_error "Health endpoint is not responding"
            echo "URL: http://localhost:8080/health"
        fi
    else
        print_warning "curl is not installed. Cannot check health endpoint"
    fi
    echo ""
}

check_ports() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Port Status"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if command -v lsof &> /dev/null; then
        echo "Port 8080 (Application):"
        if lsof -ti:8080 > /dev/null 2>&1; then
            print_success "Port 8080 is in use"
            lsof -i:8080 | tail -n +2
        else
            print_warning "Port 8080 is not in use"
        fi
        echo ""
        
        echo "Port 6379 (Redis):"
        if lsof -ti:6379 > /dev/null 2>&1; then
            print_success "Port 6379 is in use"
            lsof -i:6379 | tail -n +2
        else
            print_warning "Port 6379 is not in use"
        fi
        echo ""
        
        echo "Port 8000 (DynamoDB):"
        if lsof -ti:8000 > /dev/null 2>&1; then
            print_success "Port 8000 is in use"
            lsof -i:8000 | tail -n +2
        else
            print_warning "Port 8000 is not in use"
        fi
    else
        print_warning "lsof is not installed. Cannot check port status"
    fi
    echo ""
}

check_logs() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Recent Logs"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    LOG_FILE="$PROJECT_DIR/logs/service.log"
    
    if [ -f "$LOG_FILE" ]; then
        echo "Last 10 lines from: $LOG_FILE"
        echo ""
        tail -10 "$LOG_FILE"
    else
        print_warning "Log file not found: $LOG_FILE"
    fi
    echo ""
}

# Main execution
main() {
    echo ""
    print_info "=========================================="
    print_info "Enterprise Service Framework - Status"
    print_info "=========================================="
    echo ""
    
    check_docker_services
    check_application
    check_health
    check_ports
    check_logs
    
    print_info "=========================================="
    print_info "Status check complete"
    print_info "=========================================="
    echo ""
}

# Run main function
main "$@"

