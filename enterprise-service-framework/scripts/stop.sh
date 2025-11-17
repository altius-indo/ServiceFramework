#!/bin/bash

###############################################################################
# Enterprise Service Framework - Shutdown Script
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

stop_application() {
    print_info "Stopping application..."
    
    if [ ! -f "$PID_FILE" ]; then
        print_warning "PID file not found. Application may not be running."
        
        # Try to find process by port
        if command -v lsof &> /dev/null; then
            PORT_PID=$(lsof -ti:8080 2>/dev/null || true)
            if [ -n "$PORT_PID" ]; then
                print_info "Found process on port 8080 (PID: $PORT_PID)"
                read -p "Kill this process? (y/n) " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    kill "$PORT_PID" 2>/dev/null || true
                    print_success "Process killed"
                fi
            fi
        fi
        return
    fi
    
    APP_PID=$(cat "$PID_FILE")
    
    if ! ps -p "$APP_PID" > /dev/null 2>&1; then
        print_warning "Application is not running (stale PID file)"
        rm -f "$PID_FILE"
        return
    fi
    
    print_info "Sending SIGTERM to process $APP_PID..."
    kill "$APP_PID" 2>/dev/null || true
    
    # Wait for graceful shutdown
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if ! ps -p "$APP_PID" > /dev/null 2>&1; then
            print_success "Application stopped gracefully"
            rm -f "$PID_FILE"
            return
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    
    # Force kill if still running
    if ps -p "$APP_PID" > /dev/null 2>&1; then
        print_warning "Application did not stop gracefully. Force killing..."
        kill -9 "$APP_PID" 2>/dev/null || true
        sleep 1
        
        if ! ps -p "$APP_PID" > /dev/null 2>&1; then
            print_success "Application force stopped"
            rm -f "$PID_FILE"
        else
            print_error "Failed to stop application"
            exit 1
        fi
    fi
}

stop_dependencies() {
    local stop_deps=$1
    
    if [ "$stop_deps" != "true" ]; then
        print_info "Docker services will continue running"
        print_info "To stop them, run: docker-compose -f $DOCKER_COMPOSE_FILE down"
        return
    fi
    
    print_info "Stopping dependencies (Redis, DynamoDB)..."
    
    cd "$PROJECT_DIR"
    docker-compose -f "$DOCKER_COMPOSE_FILE" down
    
    print_success "Dependencies stopped"
}

cleanup() {
    print_info "Cleaning up..."
    
    # Remove PID file if exists
    if [ -f "$PID_FILE" ]; then
        rm -f "$PID_FILE"
    fi
    
    # Clean up any remaining processes on port 8080
    if command -v lsof &> /dev/null; then
        PORT_PID=$(lsof -ti:8080 2>/dev/null || true)
        if [ -n "$PORT_PID" ]; then
            print_warning "Found process on port 8080 (PID: $PORT_PID)"
            read -p "Kill this process? (y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                kill "$PORT_PID" 2>/dev/null || true
                print_success "Process killed"
            fi
        fi
    fi
}

# Main execution
main() {
    print_info "=========================================="
    print_info "Enterprise Service Framework - Shutdown"
    print_info "=========================================="
    echo ""
    
    # Parse arguments
    STOP_DEPS=false
    FORCE=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --stop-deps)
                STOP_DEPS=true
                shift
                ;;
            --force)
                FORCE=true
                shift
                ;;
            -h|--help)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --stop-deps    Also stop Docker services (Redis, DynamoDB)"
                echo "  --force        Force stop without confirmation"
                echo "  -h, --help     Show this help message"
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use -h or --help for usage information"
                exit 1
                ;;
        esac
    done
    
    # Confirmation
    if [ "$FORCE" != "true" ]; then
        read -p "Are you sure you want to stop the service? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Shutdown cancelled"
            exit 0
        fi
    fi
    
    stop_application
    echo ""
    
    stop_dependencies "$STOP_DEPS"
    echo ""
    
    cleanup
    echo ""
    
    print_success "=========================================="
    print_success "Service stopped successfully!"
    print_success "=========================================="
}

# Run main function
main "$@"

