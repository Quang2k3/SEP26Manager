#!/bin/bash

# SEP26Manager Deployment Script
# Usage: ./deploy.sh [environment]
# Example: ./deploy.sh production

set -e

ENVIRONMENT=${1:-development}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🚀 Starting deployment for: $ENVIRONMENT"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi
    
    if ! command -v git &> /dev/null; then
        print_error "Git is not installed"
        exit 1
    fi
    
    print_success "Prerequisites OK"
}

# Pull latest code
pull_code() {
    print_info "Pulling latest code..."
    cd "$PROJECT_ROOT"
    git pull origin main
    print_success "Code updated"
}

# Deploy backend
deploy_backend() {
    print_info "Deploying backend..."
    cd "$PROJECT_ROOT"
    
    # Build and start containers
    docker-compose pull
    docker-compose up -d --build backend
    
    # Wait for backend to be healthy
    print_info "Waiting for backend to be healthy..."
    for i in {1..30}; do
        if curl -sf http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
            print_success "Backend is healthy!"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    
    print_error "Backend failed to start"
    docker-compose logs backend
    exit 1
}

# Deploy frontend (build locally)
deploy_frontend() {
    print_info "Building frontend..."
    cd "$PROJECT_ROOT/frontend"
    
    if [ ! -d "node_modules" ]; then
        npm ci
    fi
    
    npm run build
    print_success "Frontend built successfully"
}

# Cleanup
cleanup() {
    print_info "Cleaning up..."
    cd "$PROJECT_ROOT"
    docker system prune -f
    print_success "Cleanup completed"
}

# Database backup
backup_database() {
    print_info "Backing up database..."
    BACKUP_FILE="backup_$(date +%Y%m%d_%H%M%S).sql"
    docker exec sep26manager-postgres pg_dump -U admin sep26manager > "$PROJECT_ROOT/backups/$BACKUP_FILE"
    print_success "Database backed up to: $BACKUP_FILE"
}

# Main deployment flow
main() {
    check_prerequisites
    
    # Backup before deployment
    if [ "$ENVIRONMENT" = "production" ]; then
        mkdir -p "$PROJECT_ROOT/backups"
        backup_database
    fi
    
    pull_code
    deploy_backend
    
    if [ "$ENVIRONMENT" = "local" ]; then
        deploy_frontend
    fi
    
    cleanup
    
    print_success "🎉 Deployment completed successfully!"
    print_info "Backend: http://localhost:8080"
    print_info "Frontend: http://localhost:3000"
}

# Run main
main
