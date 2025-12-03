# Hướng Dẫn Setup Database PostgreSQL

## 📋 Yêu Cầu
- PostgreSQL đã được cài đặt và đang chạy
- Quyền truy cập với user `postgres` hoặc user có quyền tạo database

## 🚀 Các Bước Thực Hiện

### Bước 1: Tạo Database (nếu chưa có)

**Cách 1: Sử dụng psql command line**
```bash
psql -U postgres
```

Sau đó chạy:
```sql
CREATE DATABASE sep26db;
\q
```

**Cách 2: Sử dụng pgAdmin**
1. Mở pgAdmin
2. Right-click vào "Databases" → "Create" → "Database"
3. Nhập tên: `sep26db`
4. Click "Save"

### Bước 2: Chạy Script SQL

**Cách 1: Sử dụng psql**
```bash
psql -U postgres -d sep26db -f database_setup.sql
```

**Cách 2: Sử dụng pgAdmin**
1. Mở pgAdmin
2. Kết nối vào database `sep26db`
3. Click vào "Query Tool" (biểu tượng bút chì)
4. Mở file `database_setup.sql`
5. Click "Execute" (F5)

**Cách 3: Copy và paste từng phần**
1. Mở file `database_setup.sql`
2. Copy từng phần và chạy trong Query Tool

### Bước 3: Kiểm Tra Kết Quả

Chạy query sau để xem dữ liệu:
```sql
SELECT id, username, email, first_name, last_name, role, enabled 
FROM users 
ORDER BY id;
```

## 🔐 Tạo Password Hash Mới

Password trong script demo là: `password123`

Để tạo password hash mới cho user, bạn có thể:

### Cách 1: Sử dụng Spring Boot Application

Tạo một class test hoặc chạy trong main method:
```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "your_password_here";
        String hashedPassword = encoder.encode(password);
        System.out.println("Hashed Password: " + hashedPassword);
    }
}
```

### Cách 2: Sử dụng Online BCrypt Generator
- Truy cập: https://bcrypt-generator.com/
- Nhập password và click "Generate Hash"
- Copy hash và sử dụng trong SQL

### Cách 3: Sử dụng Command Line (nếu có bcrypt tool)
```bash
# Install bcrypt-cli (nếu chưa có)
npm install -g bcrypt-cli

# Generate hash
bcrypt-cli "your_password" 10
```

## 📊 Cấu Trúc Database

### Bảng: users

| Column      | Type        | Constraints                    | Description              |
|-------------|-------------|--------------------------------|--------------------------|
| id          | BIGSERIAL   | PRIMARY KEY                    | ID tự động tăng          |
| username    | VARCHAR(255)| NOT NULL, UNIQUE               | Tên đăng nhập            |
| email       | VARCHAR(255)| NOT NULL, UNIQUE               | Email                    |
| password    | VARCHAR(255)| NOT NULL                       | Mật khẩu (BCrypt hash)   |
| first_name  | VARCHAR(255)| NULL                           | Tên                      |
| last_name   | VARCHAR(255)| NULL                           | Họ                       |
| enabled     | BOOLEAN     | NOT NULL, DEFAULT true         | Trạng thái kích hoạt     |
| role        | VARCHAR(50) | NOT NULL, DEFAULT 'USER'       | Vai trò (ADMIN/MANAGER/USER) |
| created_at  | TIMESTAMP   | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Thời gian tạo    |
| updated_at  | TIMESTAMP   | DEFAULT CURRENT_TIMESTAMP      | Thời gian cập nhật       |

### Indexes
- `idx_users_username`: Index trên cột username
- `idx_users_email`: Index trên cột email
- `idx_users_role`: Index trên cột role
- `idx_users_enabled`: Index trên cột enabled

### Trigger
- `update_users_updated_at`: Tự động cập nhật `updated_at` khi UPDATE

## 👥 Dữ Liệu Demo

Script tạo 7 users demo:

1. **admin** (ADMIN)
   - Email: admin@sep26.com
   - Password: password123

2. **manager1** (MANAGER)
   - Email: manager1@sep26.com
   - Password: password123

3. **john_doe** (USER)
   - Email: john.doe@example.com
   - Password: password123

4. **jane_smith** (USER)
   - Email: jane.smith@example.com
   - Password: password123

5. **bob_wilson** (USER)
   - Email: bob.wilson@example.com
   - Password: password123

6. **alice_brown** (USER)
   - Email: alice.brown@example.com
   - Password: password123

7. **disabled_user** (USER, disabled)
   - Email: disabled@example.com
   - Password: password123
   - Enabled: false

## ⚠️ Lưu Ý

1. **Password Hash**: Tất cả users demo đều dùng password: `password123`
   - Hash: `$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m`
   - Đây là hash BCrypt với cost factor 10

2. **Security**: Trong môi trường production, hãy:
   - Thay đổi tất cả passwords
   - Xóa hoặc thay đổi dữ liệu demo
   - Sử dụng strong passwords

3. **Connection**: Đảm bảo PostgreSQL đang chạy và có thể kết nối:
   ```bash
   # Kiểm tra PostgreSQL service
   # Windows:
   Get-Service postgresql-x64-*
   
   # Linux/Mac:
   sudo systemctl status postgresql
   ```

## 🔧 Troubleshooting

### Lỗi: "database sep26db does not exist"
**Giải pháp**: Tạo database trước (xem Bước 1)

### Lỗi: "permission denied"
**Giải pháp**: Đảm bảo bạn đang dùng user có quyền tạo table (thường là `postgres`)

### Lỗi: "relation users already exists"
**Giải pháp**: Bảng đã tồn tại. Script sử dụng `CREATE TABLE IF NOT EXISTS` nên sẽ không ghi đè.
Nếu muốn xóa và tạo lại:
```sql
DROP TABLE IF EXISTS users CASCADE;
-- Sau đó chạy lại script
```

### Lỗi: "Connection refused"
**Giải pháp**: 
1. Kiểm tra PostgreSQL service có đang chạy không
2. Kiểm tra port 5432 có đang lắng nghe không
3. Kiểm tra file `pg_hba.conf` để đảm bảo cho phép kết nối

## ✅ Kiểm Tra Kết Nối Từ Application

Sau khi setup database, chạy lại Spring Boot application và kiểm tra log:
- Nếu thành công: Sẽ không có lỗi kết nối database
- Nếu thất bại: Kiểm tra lại cấu hình trong `application.properties`

