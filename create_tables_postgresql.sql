-- =============================================
-- Script tạo bảng Users cho PostgreSQL
-- Database First Approach (ddl-auto=none)
-- =============================================
--
-- ⚠️ QUAN TRỌNG: Trong Database First, bạn PHẢI tạo các cột từ BaseEntity trong database!
--
-- BaseEntity.java CHỈ là code Java để map với database.
-- BaseEntity.java KHÔNG tự động tạo cột trong database.
-- 
-- Bạn PHẢI tạo các cột sau trong mỗi bảng:
--   - id (BIGSERIAL PRIMARY KEY)
--   - created_at (TIMESTAMP NOT NULL)
--   - updated_at (TIMESTAMP NULL)
--
-- Sau đó BaseEntity.java sẽ map với các cột đó.
-- =============================================

-- Tạo database nếu chưa có (chạy với quyền superuser)
-- CREATE DATABASE sep26db;
-- \c sep26db;

-- Kiểm tra và tạo bảng users
-- Bảng này PHẢI bao gồm:
--   - Các cột từ BaseEntity: id, created_at, updated_at (PHẢI có trong database!)
--   - Các cột từ User: username, email, password, first_name, last_name, enabled, role

CREATE TABLE IF NOT EXISTS users (
    -- ============================================
    -- CÁC CỘT TỪ BaseEntity (@MappedSuperclass)
    -- ============================================
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    
    -- ============================================
    -- CÁC CỘT TỪ User Entity
    -- ============================================
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    
    -- ============================================
    -- CONSTRAINTS
    -- ============================================
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- Tạo index cho created_at để tối ưu query theo thời gian
CREATE INDEX IF NOT EXISTS ix_users_created_at ON users (created_at);

-- Tạo index cho email để tối ưu tìm kiếm
CREATE INDEX IF NOT EXISTS ix_users_email ON users (email);

-- =============================================
-- XÓA DỮ LIỆU CŨ (Nếu cần reset)
-- =============================================
-- Uncomment các dòng dưới nếu muốn xóa dữ liệu cũ và reset sequence
-- TRUNCATE TABLE users RESTART IDENTITY CASCADE;
-- hoặc
-- DELETE FROM users;
-- ALTER SEQUENCE users_id_seq RESTART WITH 1;

-- =============================================
-- THÊM DỮ LIỆU DEMO
-- =============================================
-- Lưu ý: Password đã được hash bằng bcrypt
-- Password hash mẫu tương ứng với password: "password"

-- Kiểm tra xem đã có dữ liệu chưa
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users LIMIT 1) THEN
        INSERT INTO users 
            (username, email, password, first_name, last_name, enabled, role, created_at, updated_at)
        VALUES
            -- User 1: Admin
            ('admin', 
             'admin@example.com', 
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
             'Admin', 
             'User', 
             TRUE, 
             'ADMIN', 
             NOW(), 
             NOW()),
            
            -- User 2: Regular User
            ('john_doe', 
             'john.doe@example.com', 
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
             'John', 
             'Doe', 
             TRUE, 
             'USER', 
             NOW(), 
             NOW()),
            
            -- User 3: Another User
            ('jane_smith', 
             'jane.smith@example.com', 
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
             'Jane', 
             'Smith', 
             TRUE, 
             'USER', 
             NOW(), 
             NOW()),
            
            -- User 4: Manager
            ('manager01', 
             'manager@example.com', 
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
             'Manager', 
             'One', 
             TRUE, 
             'MANAGER', 
             NOW(), 
             NOW()),
            
            -- User 5: Disabled User
            ('inactive_user', 
             'inactive@example.com', 
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
             'Inactive', 
             'User', 
             FALSE, 
             'USER', 
             NOW(), 
             NOW());
        
        RAISE NOTICE 'Đã thêm 5 bản ghi demo vào bảng users!';
    ELSE
        RAISE NOTICE 'Bảng users đã có dữ liệu, bỏ qua việc insert demo data.';
    END IF;
END $$;

-- =============================================
-- KIỂM TRA DỮ LIỆU
-- =============================================
SELECT 
    id,
    username,
    email,
    first_name,
    last_name,
    enabled,
    role,
    created_at,
    updated_at
FROM users
ORDER BY id;

-- =============================================
-- THÔNG TIN BẢNG
-- =============================================
SELECT 
    column_name AS "Tên Cột",
    data_type AS "Kiểu Dữ Liệu",
    character_maximum_length AS "Độ Dài",
    is_nullable AS "Cho Phép NULL",
    column_default AS "Giá Trị Mặc Định"
FROM information_schema.columns
WHERE table_name = 'users'
ORDER BY ordinal_position;

-- =============================================
-- LƯU Ý QUAN TRỌNG - DATABASE FIRST
-- =============================================
/*
⚠️ DATABASE FIRST APPROACH (ddl-auto=none):
   - Bạn PHẢI tạo bảng trong PostgreSQL trước
   - JPA KHÔNG tự động tạo bảng từ code
   - Code Java chỉ để map với database đã có

1. BaseEntity và Database:
   - BaseEntity.java CHỈ là code Java, KHÔNG tự động tạo cột
   - Bạn PHẢI tạo các cột id, created_at, updated_at trong database
   - BaseEntity.java sẽ map với các cột đó
   - BaseEntity là @MappedSuperclass, KHÔNG tạo bảng "base_entities" riêng
   - Các cột từ BaseEntity được tích hợp vào mỗi bảng (users, products, ...)

2. Checklist khi tạo bảng mới:
   - [ ] PHẢI có cột: id BIGSERIAL PRIMARY KEY
   - [ ] PHẢI có cột: created_at TIMESTAMP NOT NULL
   - [ ] PHẢI có cột: updated_at TIMESTAMP NULL
   - [ ] Các cột riêng của entity đó

3. Khác biệt PostgreSQL vs SQL Server:
   - BIGSERIAL thay vì BIGINT IDENTITY(1,1)
   - TIMESTAMP thay vì DATETIME2
   - VARCHAR thay vì NVARCHAR
   - BOOLEAN thay vì BIT
   - NOW() thay vì GETDATE()
   - TRUE/FALSE thay vì 1/0

4. Về Password:
   - Password trong dữ liệu demo đã được hash bằng bcrypt
   - Hash mẫu: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
   - Tương ứng với password: "password"
   - Trong thực tế, password sẽ được hash tự động khi đăng ký qua API

5. Về Timestamps:
   - Bạn PHẢI tạo cột created_at và updated_at trong database trước
   - Sau đó BaseEntity.java sẽ tự động set giá trị:
     * @PrePersist: Tự động set created_at và updated_at khi INSERT
     * @PreUpdate: Tự động cập nhật updated_at khi UPDATE
   - created_at không thể sửa (updatable = false)

6. Về SERIAL/BIGSERIAL:
   - id sử dụng BIGSERIAL - tự động tăng từ 1
   - Mỗi lần INSERT, id sẽ tự động tăng
   - Bạn PHẢI tạo cột id với BIGSERIAL trong database

7. Về Constraints:
   - username: UNIQUE, NOT NULL
   - email: UNIQUE, NOT NULL
   - enabled: NOT NULL, DEFAULT TRUE
   - role: NOT NULL, DEFAULT 'USER'
*/

