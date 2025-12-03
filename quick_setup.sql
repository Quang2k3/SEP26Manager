-- ============================================
-- QUICK SETUP SCRIPT - Chạy nhanh để setup database
-- ============================================

-- Bước 1: Tạo database (chạy với user postgres, bỏ comment nếu cần)
-- CREATE DATABASE sep26db;

-- Bước 2: Kết nối vào database sep26db
-- \c sep26db

-- Bước 3: Tạo bảng users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT true,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bước 4: Tạo indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- Bước 5: Tạo trigger tự động cập nhật updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Bước 6: Xóa dữ liệu cũ (nếu có) và insert dữ liệu demo
-- Password: password123 (BCrypt hash)
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

INSERT INTO users (username, email, password, first_name, last_name, enabled, role) VALUES
    ('admin', 'admin@sep26.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'Admin', 'User', true, 'ADMIN'),
    ('manager1', 'manager1@sep26.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'Manager', 'One', true, 'MANAGER'),
    ('john_doe', 'john.doe@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'John', 'Doe', true, 'USER'),
    ('jane_smith', 'jane.smith@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'Jane', 'Smith', true, 'USER'),
    ('bob_wilson', 'bob.wilson@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'Bob', 'Wilson', true, 'USER'),
    ('alice_brown', 'alice.brown@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'Alice', 'Brown', true, 'USER'),
    ('disabled_user', 'disabled@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ3m', 'Disabled', 'User', false, 'USER');

-- Bước 7: Xem kết quả
SELECT 
    id, 
    username, 
    email, 
    first_name || ' ' || last_name as full_name,
    role,
    enabled,
    created_at
FROM users 
ORDER BY id;

-- Thống kê
SELECT 
    role,
    COUNT(*) as total,
    COUNT(CASE WHEN enabled THEN 1 END) as enabled_count
FROM users
GROUP BY role;

