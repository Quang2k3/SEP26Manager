# 🐳 Docker Commands Cheat Sheet - SEP26 Manager

## 🚀 Start Docker (Lần sau chạy lại)

### Option 1: Chỉ Infrastructure (Recommended cho Development)
```bash
# Chuyển vào thư mục project
cd c:\Users\Public\Documents\ProjectSEP26\SEP26Manager

# Start PostgreSQL + Redis
docker-compose up -d postgres redis

# Backend chạy local
mvn spring-boot:run
```

### Option 2: Chạy tất cả (Full Docker)
```bash
# Start tất cả services (postgres, redis, backend)
docker-compose up -d

# Hoặc với rebuild (nếu có thay đổi code)
docker-compose up -d --build
```

---

## 📊 Kiểm tra trạng thái

```bash
# Xem containers đang chạy
docker ps

# Xem tất cả containers (kể cả stopped)
docker ps -a

# Xem logs real-time
docker-compose logs -f

# Xem logs của 1 service cụ thể
docker-compose logs -f backend
docker-compose logs -f postgres
docker-compose logs -f redis
```

---

## 🛑 Stop Docker

```bash
# Stop tất cả services
docker-compose down

# Stop và xóa volumes (⚠️ MẤT DATA!)
docker-compose down -v

# Stop 1 service cụ thể
docker-compose stop backend
docker-compose stop postgres
docker-compose stop redis
```

---

## 🔄 Restart Services

```bash
# Restart tất cả
docker-compose restart

# Restart 1 service
docker-compose restart backend
docker-compose restart postgres
docker-compose restart redis

# Restart sau khi sửa code (rebuild)
docker-compose up -d --build backend
```

---

## 🧪 Test & Debug

### Test Redis
```bash
# Test ping
docker exec -it sep26manager-redis redis-cli ping

# Vào Redis CLI
docker exec -it sep26manager-redis redis-cli

# Trong Redis CLI:
127.0.0.1:6379> KEYS *              # Xem tất cả keys
127.0.0.1:6379> GET otp:email@test  # Xem OTP của email
127.0.0.1:6379> TTL otp:email@test  # Xem thời gian hết hạn (giây)
127.0.0.1:6379> exit
```

### Test PostgreSQL
```bash
# Vào PostgreSQL
docker exec -it sep26manager-postgres psql -U postgres -d SEP26WMS

# Trong PostgreSQL:
SEP26WMS=# \dt                              # List tables
SEP26WMS=# SELECT * FROM users LIMIT 5;    # Xem users
SEP26WMS=# SELECT email, is_first_login FROM users;
SEP26WMS=# \q                               # Exit
```

### Test Backend Health
```bash
curl http://localhost:8080/actuator/health
```

---

## 🧹 Cleanup (Dọn dẹp)

```bash
# Xóa tất cả stopped containers
docker container prune

# Xóa tất cả unused images
docker image prune -a

# Xóa tất cả unused volumes
docker volume prune

# Xóa tất cả (CẢNH BÁO: MẤT TOÀN BỘ DATA!)
docker system prune -a --volumes
```

---

## 📝 Workflow hàng ngày

### Morning (Bắt đầu làm việc)
```bash
cd c:\Users\Public\Documents\ProjectSEP26\SEP26Manager

# Start infrastructure
docker-compose up -d postgres redis

# Kiểm tra
docker ps

# Run backend local
mvn spring-boot:run
```

### Coding (Đang code)
```bash
# Xem logs khi test
docker-compose logs -f redis

# Test Redis có OTP không
docker exec -it sep26manager-redis redis-cli KEYS "otp:*"

# Xem database
docker exec -it sep26manager-postgres psql -U postgres -d SEP26WMS
```

### Evening (Kết thúc làm việc)
```bash
# Ctrl+C để stop backend (nếu chạy local)

# Stop Docker containers
docker-compose down

# Hoặc để chạy (không tốn tài nguyên nhiều)
# Không cần down nếu muốn giữ containers
```

---

## ⚠️ Troubleshooting

### Problem: Port already in use
```bash
# Xem process nào đang dùng port
netstat -ano | findstr :5432    # PostgreSQL
netstat -ano | findstr :6379    # Redis
netstat -ano | findstr :8080    # Backend

# Kill process (thay PID)
taskkill /PID <PID> /F
```

### Problem: Container không start
```bash
# Xem logs lỗi
docker-compose logs postgres
docker-compose logs redis

# Restart lại
docker-compose restart postgres redis

# Hoặc stop và start lại
docker-compose down
docker-compose up -d postgres redis
```

### Problem: Database connection failed
```bash
# Check PostgreSQL is healthy
docker ps | grep postgres

# Test connection
docker exec -it sep26manager-postgres pg_isready -U postgres

# Xem logs
docker-compose logs postgres
```

### Problem: Redis connection failed
```bash
# Check Redis is healthy
docker exec -it sep26manager-redis redis-cli ping

# Xem logs
docker-compose logs redis

# Restart
docker-compose restart redis
```

---

## 🎯 Quick Reference

| Mục đích | Command |
|----------|---------|
| **Start** | `docker-compose up -d postgres redis` |
| **Stop** | `docker-compose down` |
| **Restart** | `docker-compose restart` |
| **Logs** | `docker-compose logs -f` |
| **Status** | `docker ps` |
| **Test Redis** | `docker exec -it sep26manager-redis redis-cli ping` |
| **Test DB** | `docker exec -it sep26manager-postgres psql -U postgres -d SEP26WMS` |
| **Rebuild** | `docker-compose up -d --build backend` |

---

**💡 Tip**: Thêm alias vào PowerShell profile để gõ nhanh hơn:
```powershell
# Edit profile: notepad $PROFILE
function dcu { docker-compose up -d postgres redis }
function dcd { docker-compose down }
function dcl { docker-compose logs -f }
function dcp { docker ps }
```

Sau đó chỉ cần gõ: `dcu`, `dcd`, `dcl`, `dcp` 🚀
