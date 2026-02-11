# SEP26Manager Deployment Script (PowerShell)
# Usage: .\deploy.ps1 -Environment "production"

param(
    [string]$Environment = "development"
)

$ErrorActionPreference = "Stop"

Write-Host "🚀 Starting deployment for: $Environment" -ForegroundColor Cyan

# Functions
function Print-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Print-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Print-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Yellow
}

# Check prerequisites
function Check-Prerequisites {
    Print-Info "Checking prerequisites..."
    
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
        Print-Error "Docker is not installed"
        exit 1
    }
    
    if (!(Get-Command git -ErrorAction SilentlyContinue)) {
        Print-Error "Git is not installed"
        exit 1
    }
    
    Print-Success "Prerequisites OK"
}

# Pull latest code
function Pull-Code {
    Print-Info "Pulling latest code..."
    git pull origin main
    Print-Success "Code updated"
}

# Deploy backend
function Deploy-Backend {
    Print-Info "Deploying backend..."
    
    # Build and start containers
    docker-compose pull
    docker-compose up -d --build backend
    
    # Wait for backend to be healthy
    Print-Info "Waiting for backend to be healthy..."
    $maxAttempts = 30
    for ($i = 1; $i -le $maxAttempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/actuator/health" -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Print-Success "Backend is healthy!"
                return
            }
        } catch {
            Write-Host "." -NoNewline
            Start-Sleep -Seconds 2
        }
    }
    
    Print-Error "Backend failed to start"
    docker-compose logs backend
    exit 1
}

# Deploy frontend
function Deploy-Frontend {
    Print-Info "Building frontend..."
    Set-Location -Path ".\frontend"
    
    if (!(Test-Path ".\node_modules")) {
        npm ci
    }
    
    npm run build
    Print-Success "Frontend built successfully"
    Set-Location -Path ".."
}

# Cleanup
function Cleanup {
    Print-Info "Cleaning up..."
    docker system prune -f
    Print-Success "Cleanup completed"
}

# Database backup
function Backup-Database {
    Print-Info "Backing up database..."
    $backupDir = ".\backups"
    if (!(Test-Path $backupDir)) {
        New-Item -ItemType Directory -Path $backupDir
    }
    
    $backupFile = "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"
    docker exec sep26manager-postgres pg_dump -U admin sep26manager | Out-File -FilePath "$backupDir\$backupFile"
    Print-Success "Database backed up to: $backupFile"
}

# Main deployment
try {
    Check-Prerequisites
    
    if ($Environment -eq "production") {
        Backup-Database
    }
    
    Pull-Code
    Deploy-Backend
    
    if ($Environment -eq "local") {
        Deploy-Frontend
    }
    
    Cleanup
    
    Print-Success "🎉 Deployment completed successfully!"
    Print-Info "Backend: http://localhost:8080"
    Print-Info "Frontend: http://localhost:3000"
} catch {
    Print-Error "Deployment failed: $_"
    exit 1
}
