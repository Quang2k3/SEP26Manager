# Hướng Dẫn Chuyển Đổi Từ SQL Server Sang PostgreSQL

## ✅ Đã Cập Nhật

### 1. `application.properties`
- ✅ Đã chuyển JDBC URL sang PostgreSQL
- ✅ Đã đổi driver sang `org.postgresql.Driver`
- ✅ Đã đổi dialect sang `PostgreSQLDialect`
- ✅ Đã cập nhật port: 5432 (mặc định PostgreSQL)

### 2. `pom.xml`
- ✅ Đã thay dependency `mssql-jdbc` bằng `postgresql`

### 3. Script SQL
- ✅ Đã tạo `create_tables_postgresql.sql` với cú pháp PostgreSQL

---

## 📋 Các Thay Đổi Chi Tiết

### application.properties

#### Trước (SQL Server):
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=Sep26Db;encrypt=false;trustServerCertificate=true
spring.datasource.driverClassName=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.datasource.username=sa
spring.datasource.password=123
spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect
```

#### Sau (PostgreSQL):
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sep26db
spring.datasource.driverClassName=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### pom.xml

#### Trước:
```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
```

#### Sau:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## 🚀 Các Bước Setup

### Bước 1: Cài Đặt PostgreSQL

1. Tải và cài đặt PostgreSQL từ: https://www.postgresql.org/download/
2. Hoặc sử dụng Docker:
   ```bash
   docker run --name postgres-sep26 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=sep26db -p 5432:5432 -d postgres
   ```

### Bước 2: Tạo Database

Mở pgAdmin 4 hoặc psql và chạy:

```sql
CREATE DATABASE sep26db;
```

Hoặc sử dụng psql command line:
```bash
psql -U postgres
CREATE DATABASE sep26db;
\q
```

### Bước 3: Cập Nhật Thông Tin Kết Nối

Sửa file `application.properties` nếu cần:

```properties
# Nếu username/password khác
spring.datasource.username=your_username
spring.datasource.password=your_password

# Nếu port khác
spring.datasource.url=jdbc:postgresql://localhost:5433/sep26db

# Nếu database name khác
spring.datasource.url=jdbc:postgresql://localhost:5432/your_database_name
```

### Bước 4: Tạo Bảng

Chạy script SQL trong pgAdmin 4 hoặc psql:

```bash
psql -U postgres -d sep26db -f create_tables_postgresql.sql
```

Hoặc copy nội dung file `create_tables_postgresql.sql` và chạy trong pgAdmin 4.

### Bước 5: Cập Nhật Dependencies

Chạy Maven để tải dependency mới:

```bash
mvn clean install
```

Hoặc nếu dùng IDE, refresh Maven project.

### Bước 6: Chạy Ứng Dụng

```bash
mvn spring-boot:run
```

---

## 🔄 Khác Biệt Giữa SQL Server và PostgreSQL

| SQL Server | PostgreSQL | Ghi Chú |
|------------|------------|---------|
| `BIGINT IDENTITY(1,1)` | `BIGSERIAL` | Auto-increment |
| `DATETIME2` | `TIMESTAMP` | Date/Time |
| `NVARCHAR(n)` | `VARCHAR(n)` | String |
| `BIT` | `BOOLEAN` | Boolean |
| `GETDATE()` | `NOW()` | Current timestamp |
| `1/0` | `TRUE/FALSE` | Boolean values |
| `DBCC CHECKIDENT` | `ALTER SEQUENCE` | Reset identity |

---

## 📝 Ví Dụ Tạo Bảng

### SQL Server:
```sql
CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    created_at DATETIME2 NOT NULL,
    username NVARCHAR(255) NOT NULL,
    enabled BIT NOT NULL DEFAULT 1
);
```

### PostgreSQL:
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    username VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
```

---

## ⚠️ Lưu Ý Quan Trọng

1. **Database Name**: PostgreSQL phân biệt chữ hoa/thường. Nếu tên database có chữ hoa, phải đặt trong dấu ngoặc kép: `"Sep26Db"`

2. **Username/Password**: Mặc định PostgreSQL có user `postgres` với password bạn đã set khi cài đặt.

3. **Port**: Mặc định PostgreSQL chạy trên port `5432`.

4. **Schema**: PostgreSQL sử dụng schema `public` mặc định. Nếu cần dùng schema khác, thêm vào JDBC URL:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/sep26db?currentSchema=your_schema
   ```

5. **Connection Pool**: HikariCP đã được cấu hình sẵn, không cần thay đổi.

---

## 🧪 Kiểm Tra Kết Nối

### Test Connection trong pgAdmin 4:
1. Mở pgAdmin 4
2. Right-click vào "Servers" → "Create" → "Server"
3. Nhập thông tin:
   - Name: `SEP26 Local`
   - Host: `localhost`
   - Port: `5432`
   - Username: `postgres`
   - Password: `postgres` (hoặc password bạn đã set)
4. Click "Save"

### Test từ Command Line:
```bash
psql -U postgres -d sep26db -c "SELECT version();"
```

### Test từ Application:
Khi chạy ứng dụng, nếu kết nối thành công, bạn sẽ thấy log:
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

---

## 🐛 Troubleshooting

### Lỗi: "Connection refused"
- Kiểm tra PostgreSQL đã chạy chưa
- Kiểm tra port có đúng không (5432)
- Kiểm tra firewall

### Lỗi: "Authentication failed"
- Kiểm tra username/password trong `application.properties`
- Kiểm tra file `pg_hba.conf` nếu cần

### Lỗi: "Database does not exist"
- Tạo database trước: `CREATE DATABASE sep26db;`

### Lỗi: "Table does not exist"
- Chạy script `create_tables_postgresql.sql` để tạo bảng

---

## ✅ Checklist

- [ ] PostgreSQL đã được cài đặt và chạy
- [ ] Database `sep26db` đã được tạo
- [ ] `application.properties` đã được cập nhật
- [ ] `pom.xml` đã được cập nhật
- [ ] Đã chạy `mvn clean install` để tải dependency mới
- [ ] Đã chạy script `create_tables_postgresql.sql` để tạo bảng
- [ ] Đã test kết nối thành công
- [ ] Ứng dụng chạy không có lỗi

---

## 📚 Tài Liệu Tham Khảo

- PostgreSQL Official Docs: https://www.postgresql.org/docs/
- Spring Boot PostgreSQL: https://spring.io/guides/gs/accessing-data-jpa/
- pgAdmin 4: https://www.pgadmin.org/

