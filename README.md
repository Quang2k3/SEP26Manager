# SEP26Manager - Backend API

> **Spring Boot Backend + PostgreSQL Database**
> 
> Team Frontend sẽ connect tới API này

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven (optional, có trong project)

### Local Development
```bash
# Clone repository
git clone https://github.com/Quang2k3/SEP26Manager.git
cd SEP26Manager

# Setup environment
cp .env.example .env

# Start backend + database
docker-compose up -d

# Verify
curl http://localhost:8080/actuator/health
```

**Running:**
- 🚀 API: http://localhost:8080
- 🐘 PostgreSQL: localhost:5432
- 🔧 pgAdmin: http://localhost:5050 (optional)

---

## 🏗️ Tech Stack

- **Backend**: Java 17, Spring Boot 3.x, Maven
- **Database**: PostgreSQL 15
- **Security**: JWT Authentication
- **Container**: Docker, Docker Compose
- **CI/CD**: GitHub Actions
- **API Docs**: Swagger/OpenAPI

---

## 📦 Project Structure

```
SEP26Manager/
├── .github/
│   └── workflows/
│       └── backend-ci.yml          # CI/CD pipeline
├── src/
│   ├── main/
│   │   ├── java/                   # Source code
│   │   └── resources/
│   │       ├── application.yml     # Config
│   │       └── application-prod.yml
│   └── test/                       # Tests
├── scripts/
│   ├── deploy.sh                   # Linux/Mac deployment
│   └── deploy.ps1                  # Windows deployment
├── docs/                           # Documentation
│   ├── BACKEND_DEPLOYMENT.md       # 📖 Main deployment guide
│   ├── AUTH_API_GUIDE.md           # Authentication endpoints
│   ├── SWAGGER_GUIDE.md            # API documentation
│   ├── DATABASE_SETUP_GUIDE.md     # Database setup
│   └── ... (more docs)
├── .env.example                    # Environment template
├── docker-compose.yml              # Local development
├── Dockerfile                      # Container build
├── pom.xml                         # Maven config
└── README.md                       # This file
```

---

## 🔧 Development Commands

### Docker (Recommended)
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f backend

# Restart backend
docker-compose restart backend

# Stop all
docker-compose down

# Rebuild
docker-compose up -d --build
```

### Maven (Local)
```bash
# Build
mvn clean install

# Run tests
mvn test

# Run application
mvn spring-boot:run

# Package JAR
mvn package
```

### Database
```bash
# Connect to PostgreSQL
docker exec -it sep26manager-postgres psql -U admin -d sep26manager

# Backup database
docker exec sep26manager-postgres pg_dump -U admin sep26manager > backup.sql

# Restore database
cat backup.sql | docker exec -i sep26manager-postgres psql -U admin sep26manager
```

---

## 🔐 Configuration

### Environment Variables (.env)

```bash
# Database
POSTGRES_DB=sep26manager
POSTGRES_USER=admin
POSTGRES_PASSWORD=your-strong-password
POSTGRES_PORT=5432

# Spring Boot
SPRING_PROFILE=dev                  # dev, prod
BACKEND_PORT=8080

# JWT
JWT_SECRET=your-256-bit-secret-key
JWT_EXPIRATION=86400000             # 24 hours

# CORS (Frontend URL)
FRONTEND_URL=http://localhost:3000

# Docker Hub (Production)
DOCKER_USERNAME=your-dockerhub-username
```

### Application Profiles

- **dev** (`application.yml`): Local development
- **prod** (`application-prod.yml`): Production deployment

---

## 📡 API Endpoints

### Base URL
- **Local**: `http://localhost:8080`
- **Production**: `https://api.sep26manager.com`

### Health Check
```bash
GET /actuator/health
```

### API Documentation
```bash
# Swagger UI (when running)
http://localhost:8080/swagger-ui.html

# OpenAPI JSON
http://localhost:8080/v3/api-docs
```

### Example Endpoints
See [AUTH_API_GUIDE.md](./AUTH_API_GUIDE.md) and [SWAGGER_GUIDE.md](./SWAGGER_GUIDE.md)

---

## ☁️ Production Deployment

### Option 1: VPS Deployment (Docker)

**Full Guide**: [BACKEND_DEPLOYMENT.md](./BACKEND_DEPLOYMENT.md)

```bash
# On VPS
git clone <your-repo>
cd SEP26Manager
cp .env.example .env
# Edit .env with production values
docker-compose up -d
```

### Option 2: Auto Deploy (GitHub Actions)

1. Setup GitHub Secrets (see [BACKEND_DEPLOYMENT.md](./BACKEND_DEPLOYMENT.md))
2. Push to `main` branch
3. GitHub Actions auto-deploy! ✨

---

## 📚 Documentation

### Deployment & DevOps
- **[BACKEND_DEPLOYMENT.md](./BACKEND_DEPLOYMENT.md)** - Complete deployment guide ⭐
- **[SEPARATE_REPOS_GUIDE.md](./SEPARATE_REPOS_GUIDE.md)** - Separate frontend/backend repos setup
- **[DATABASE_SETUP_GUIDE.md](./DATABASE_SETUP_GUIDE.md)** - Database configuration

### API & Development
- **[AUTH_API_GUIDE.md](./AUTH_API_GUIDE.md)** - Authentication endpoints
- **[SWAGGER_GUIDE.md](./SWAGGER_GUIDE.md)** - API documentation setup
- **[SOURCE_CODE_OVERVIEW.md](./SOURCE_CODE_OVERVIEW.md)** - Code structure

### Architecture
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - System architecture
- **[CLEAN_ARCHITECTURE_ANALYSIS.md](./CLEAN_ARCHITECTURE_ANALYSIS.md)** - Clean architecture
- **[FLOW_EXPLANATION.md](./FLOW_EXPLANATION.md)** - Application flow
- **[ANNOTATION_ANALYSIS.md](./ANNOTATION_ANALYSIS.md)** - Spring annotations

---

## 🤝 Integration với Frontend Team

### CORS Configuration
Update `FRONTEND_URL` trong `.env`:
```bash
FRONTEND_URL=https://your-frontend-url.com
```

### API Base URL cho Frontend
```bash
# Development
http://localhost:8080

# Production
https://api.sep26manager.com
```

### Authentication
- **Type**: JWT Bearer Token
- **Header**: `Authorization: Bearer <token>`
- **Docs**: [AUTH_API_GUIDE.md](./AUTH_API_GUIDE.md)

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=UserServiceTest

# Integration tests
mvn verify -Pintegration-tests

# Test coverage
mvn jacoco:report
# Report: target/site/jacoco/index.html
```

---

## 🚨 Troubleshooting

### Backend won't start?
```bash
# Check logs
docker-compose logs backend

# Check Java version
java -version  # Should be 17+

# Check port availability
netstat -ano | findstr :8080
```

### Database issues?
```bash
# Check PostgreSQL running
docker ps | grep postgres

# Test connection
docker exec sep26manager-postgres pg_isready -U admin

# Restart database
docker-compose restart postgres
```

### Port conflicts?
Edit `.env`:
```bash
BACKEND_PORT=8081
POSTGRES_PORT=5433
```

**Full troubleshooting**: [BACKEND_DEPLOYMENT.md](./BACKEND_DEPLOYMENT.md#troubleshooting)

---

## 📋 Pre-Production Checklist

- [ ] All tests passing: `mvn test`
- [ ] Environment variables configured
- [ ] Database migrations tested
- [ ] CORS configured for frontend URL
- [ ] JWT secret is strong (256-bit)
- [ ] API documentation up-to-date
- [ ] Health checks working
- [ ] Database backup strategy in place

---

## 🔄 Deployment Workflow

```
Developer → Push to GitHub
     ↓
GitHub Actions
     ├── Run Tests
     ├── Build Docker Image
     ├── Push to Docker Hub
     └── Deploy to VPS
     ↓
Backend API Running ✅
```

---

## 📞 Support

- **Issues**: GitHub Issues
- **Documentation**: Check `/docs` folder
- **Main Guide**: [BACKEND_DEPLOYMENT.md](./BACKEND_DEPLOYMENT.md)

---

## 🎯 Next Steps

1. **Local Development**: Run `docker-compose up -d`
2. **Production Deployment**: Follow [BACKEND_DEPLOYMENT.md](./BACKEND_DEPLOYMENT.md)
3. **Share API with Frontend**: Provide base URL and [AUTH_API_GUIDE.md](./AUTH_API_GUIDE.md)

---

**Backend Team** 🚀
# Auto deploy test
# Test part 2
# Fix deploy test part 3
# Fix deploy test part 4
