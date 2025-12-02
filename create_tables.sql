-- =============================================
-- Script tạo bảng Users cho SQL Server
-- Database First Approach (ddl-auto=none)
-- =============================================
--
-- ⚠️ QUAN TRỌNG: Trong Database First, bạn PHẢI tạo các cột từ BaseEntity trong database!
--
-- BaseEntity.java CHỈ là code Java để map với database.
-- BaseEntity.java KHÔNG tự động tạo cột trong database.
-- 
-- Bạn PHẢI tạo các cột sau trong mỗi bảng:
--   - id (BIGINT IDENTITY(1,1) PRIMARY KEY)
--   - created_at (DATETIME2 NOT NULL)
--   - updated_at (DATETIME2 NULL)
--
-- Sau đó BaseEntity.java sẽ map với các cột đó.
-- =============================================

-- Kiểm tra và tạo bảng users
-- Bảng này PHẢI bao gồm:
--   - Các cột từ BaseEntity: id, created_at, updated_at (PHẢI có trong database!)
--   - Các cột từ User: username, email, password, first_name, last_name, enabled, role

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[users]') AND type in (N'U'))
BEGIN
    CREATE TABLE [dbo].[users] (
        -- ============================================
        -- CÁC TRƯỜNG TỪ BaseEntity (@MappedSuperclass)
        -- ============================================
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [created_at] DATETIME2 NOT NULL,
        [updated_at] DATETIME2 NULL,
        
        -- ============================================
        -- CÁC TRƯỜNG TỪ User Entity
        -- ============================================
        [username] NVARCHAR(255) NOT NULL,
        [email] NVARCHAR(255) NOT NULL,
        [password] NVARCHAR(255) NOT NULL,
        [first_name] NVARCHAR(255) NULL,
        [last_name] NVARCHAR(255) NULL,
        [enabled] BIT NOT NULL DEFAULT 1,
        [role] NVARCHAR(50) NOT NULL DEFAULT 'USER',
        
        -- ============================================
        -- CONSTRAINTS
        -- ============================================
        CONSTRAINT [PK_users] PRIMARY KEY CLUSTERED ([id] ASC),
        CONSTRAINT [UK_users_username] UNIQUE NONCLUSTERED ([username] ASC),
        CONSTRAINT [UK_users_email] UNIQUE NONCLUSTERED ([email] ASC)
    );
    
    -- Tạo index cho created_at để tối ưu query theo thời gian
    CREATE NONCLUSTERED INDEX [IX_users_created_at] ON [dbo].[users] ([created_at] ASC);
    
    -- Tạo index cho email để tối ưu tìm kiếm
    CREATE NONCLUSTERED INDEX [IX_users_email] ON [dbo].[users] ([email] ASC);
    
    PRINT 'Bảng users đã được tạo thành công!';
    PRINT 'Bảng bao gồm các trường từ BaseEntity (id, created_at, updated_at) và User entity.';
END
ELSE
BEGIN
    PRINT 'Bảng users đã tồn tại!';
END
GO

-- =============================================
-- XÓA DỮ LIỆU CŨ (Nếu cần reset)
-- =============================================
-- Uncomment các dòng dưới nếu muốn xóa dữ liệu cũ và reset IDENTITY
-- DELETE FROM [dbo].[users];
-- DBCC CHECKIDENT ('users', RESEED, 0);
-- GO

-- =============================================
-- THÊM DỮ LIỆU DEMO
-- =============================================
-- Lưu ý: Password đã được hash bằng bcrypt
-- Password hash mẫu tương ứng với password: "password"

-- Kiểm tra xem đã có dữ liệu chưa
IF NOT EXISTS (SELECT 1 FROM [dbo].[users])
BEGIN
    INSERT INTO [dbo].[users] 
        ([username], [email], [password], [first_name], [last_name], [enabled], [role], [created_at], [updated_at])
    VALUES
        -- User 1: Admin
        (N'admin', 
         N'admin@example.com', 
         N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
         N'Admin', 
         N'User', 
         1, 
         N'ADMIN', 
         GETDATE(), 
         GETDATE()),
        
        -- User 2: Regular User
        (N'john_doe', 
         N'john.doe@example.com', 
         N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
         N'John', 
         N'Doe', 
         1, 
         N'USER', 
         GETDATE(), 
         GETDATE()),
        
        -- User 3: Another User
        (N'jane_smith', 
         N'jane.smith@example.com', 
         N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
         N'Jane', 
         N'Smith', 
         1, 
         N'USER', 
         GETDATE(), 
         GETDATE()),
        
        -- User 4: Manager
        (N'manager01', 
         N'manager@example.com', 
         N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
         N'Manager', 
         N'One', 
         1, 
         N'MANAGER', 
         GETDATE(), 
         GETDATE()),
        
        -- User 5: Disabled User
        (N'inactive_user', 
         N'inactive@example.com', 
         N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
         N'Inactive', 
         N'User', 
         0, 
         N'USER', 
         GETDATE(), 
         GETDATE());
    
    PRINT 'Đã thêm 5 bản ghi demo vào bảng users!';
END
ELSE
BEGIN
    PRINT 'Bảng users đã có dữ liệu, bỏ qua việc insert demo data.';
END
GO

-- =============================================
-- KIỂM TRA DỮ LIỆU
-- =============================================
PRINT '';
PRINT '=== KIỂM TRA DỮ LIỆU ===';
SELECT 
    [id],
    [username],
    [email],
    [first_name],
    [last_name],
    [enabled],
    [role],
    [created_at],
    [updated_at]
FROM [dbo].[users]
ORDER BY [id];
GO

-- =============================================
-- THÔNG TIN BẢNG
-- =============================================
PRINT '';
PRINT '=== THÔNG TIN BẢNG ===';
SELECT 
    COLUMN_NAME AS 'Tên Cột',
    DATA_TYPE AS 'Kiểu Dữ Liệu',
    CHARACTER_MAXIMUM_LENGTH AS 'Độ Dài',
    IS_NULLABLE AS 'Cho Phép NULL',
    COLUMN_DEFAULT AS 'Giá Trị Mặc Định'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'users'
ORDER BY ORDINAL_POSITION;
GO

-- =============================================
-- LƯU Ý QUAN TRỌNG - DATABASE FIRST
-- =============================================
/*
⚠️ DATABASE FIRST APPROACH (ddl-auto=none):
   - Bạn PHẢI tạo bảng trong SQL Server trước
   - JPA KHÔNG tự động tạo bảng từ code
   - Code Java chỉ để map với database đã có

1. BaseEntity và Database:
   - BaseEntity.java CHỈ là code Java, KHÔNG tự động tạo cột
   - Bạn PHẢI tạo các cột id, created_at, updated_at trong database
   - BaseEntity.java sẽ map với các cột đó
   - BaseEntity là @MappedSuperclass, KHÔNG tạo bảng "base_entities" riêng
   - Các cột từ BaseEntity được tích hợp vào mỗi bảng (users, products, ...)

2. Checklist khi tạo bảng mới:
   - [ ] PHẢI có cột: id BIGINT IDENTITY(1,1) PRIMARY KEY
   - [ ] PHẢI có cột: created_at DATETIME2 NOT NULL
   - [ ] PHẢI có cột: updated_at DATETIME2 NULL
   - [ ] Các cột riêng của entity đó

3. Về Password:
   - Password trong dữ liệu demo đã được hash bằng bcrypt
   - Hash mẫu: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
   - Tương ứng với password: "password"
   - Trong thực tế, password sẽ được hash tự động khi đăng ký qua API

4. Về Timestamps:
   - Bạn PHẢI tạo cột created_at và updated_at trong database trước
   - Sau đó BaseEntity.java sẽ tự động set giá trị:
     * @PrePersist: Tự động set created_at và updated_at khi INSERT
     * @PreUpdate: Tự động cập nhật updated_at khi UPDATE
   - created_at không thể sửa (updatable = false)

5. Về IDENTITY:
   - id sử dụng IDENTITY(1,1) - tự động tăng từ 1
   - Mỗi lần INSERT, id sẽ tự động tăng
   - Bạn PHẢI tạo cột id với IDENTITY trong database

6. Về Constraints:
   - username: UNIQUE, NOT NULL
   - email: UNIQUE, NOT NULL
   - enabled: NOT NULL, DEFAULT 1 (true)
   - role: NOT NULL, DEFAULT 'USER'
*/

