-- =============================================
-- Script tạo NHIỀU BẢNG - Mỗi bảng đều có các trường từ BaseEntity
-- SQL Server
-- 
-- Ví dụ minh họa: User, Product, Order, Category
-- Tất cả đều extends BaseEntity → tất cả đều có id, created_at, updated_at
-- =============================================

-- =============================================
-- 1. BẢNG USERS (từ User entity)
-- =============================================
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[users]') AND type in (N'U'))
BEGIN
    CREATE TABLE [dbo].[users] (
        -- Các trường từ BaseEntity
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [created_at] DATETIME2 NOT NULL,
        [updated_at] DATETIME2 NULL,
        
        -- Các trường từ User entity
        [username] NVARCHAR(255) NOT NULL,
        [email] NVARCHAR(255) NOT NULL,
        [password] NVARCHAR(255) NOT NULL,
        [first_name] NVARCHAR(255) NULL,
        [last_name] NVARCHAR(255) NULL,
        [enabled] BIT NOT NULL DEFAULT 1,
        [role] NVARCHAR(50) NOT NULL DEFAULT 'USER',
        
        CONSTRAINT [PK_users] PRIMARY KEY CLUSTERED ([id] ASC),
        CONSTRAINT [UK_users_username] UNIQUE NONCLUSTERED ([username] ASC),
        CONSTRAINT [UK_users_email] UNIQUE NONCLUSTERED ([email] ASC)
    );
    
    CREATE NONCLUSTERED INDEX [IX_users_created_at] ON [dbo].[users] ([created_at] ASC);
    PRINT 'Bảng users đã được tạo thành công!';
END
ELSE
BEGIN
    PRINT 'Bảng users đã tồn tại!';
END
GO

-- =============================================
-- 2. BẢNG PRODUCTS (từ Product entity - ví dụ)
-- =============================================
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[products]') AND type in (N'U'))
BEGIN
    CREATE TABLE [dbo].[products] (
        -- Các trường từ BaseEntity (TỰ ĐỘNG có khi extends BaseEntity)
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [created_at] DATETIME2 NOT NULL,
        [updated_at] DATETIME2 NULL,
        
        -- Các trường từ Product entity
        [name] NVARCHAR(255) NOT NULL,
        [description] NVARCHAR(MAX) NULL,
        [price] DECIMAL(18,2) NOT NULL,
        [stock_quantity] INT NOT NULL DEFAULT 0,
        [category_id] BIGINT NULL,
        [is_active] BIT NOT NULL DEFAULT 1,
        
        CONSTRAINT [PK_products] PRIMARY KEY CLUSTERED ([id] ASC),
        CONSTRAINT [UK_products_name] UNIQUE NONCLUSTERED ([name] ASC)
    );
    
    CREATE NONCLUSTERED INDEX [IX_products_created_at] ON [dbo].[products] ([created_at] ASC);
    CREATE NONCLUSTERED INDEX [IX_products_category_id] ON [dbo].[products] ([category_id] ASC);
    PRINT 'Bảng products đã được tạo thành công!';
END
ELSE
BEGIN
    PRINT 'Bảng products đã tồn tại!';
END
GO

-- =============================================
-- 3. BẢNG ORDERS (từ Order entity - ví dụ)
-- =============================================
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[orders]') AND type in (N'U'))
BEGIN
    CREATE TABLE [dbo].[orders] (
        -- Các trường từ BaseEntity (TỰ ĐỘNG có khi extends BaseEntity)
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [created_at] DATETIME2 NOT NULL,
        [updated_at] DATETIME2 NULL,
        
        -- Các trường từ Order entity
        [user_id] BIGINT NOT NULL,
        [total_amount] DECIMAL(18,2) NOT NULL,
        [status] NVARCHAR(50) NOT NULL DEFAULT 'PENDING',
        [shipping_address] NVARCHAR(500) NULL,
        [notes] NVARCHAR(MAX) NULL,
        
        CONSTRAINT [PK_orders] PRIMARY KEY CLUSTERED ([id] ASC),
        CONSTRAINT [FK_orders_users] FOREIGN KEY ([user_id]) 
            REFERENCES [dbo].[users] ([id]) ON DELETE CASCADE
    );
    
    CREATE NONCLUSTERED INDEX [IX_orders_created_at] ON [dbo].[orders] ([created_at] ASC);
    CREATE NONCLUSTERED INDEX [IX_orders_user_id] ON [dbo].[orders] ([user_id] ASC);
    CREATE NONCLUSTERED INDEX [IX_orders_status] ON [dbo].[orders] ([status] ASC);
    PRINT 'Bảng orders đã được tạo thành công!';
END
ELSE
BEGIN
    PRINT 'Bảng orders đã tồn tại!';
END
GO

-- =============================================
-- 4. BẢNG CATEGORIES (từ Category entity - ví dụ)
-- =============================================
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[categories]') AND type in (N'U'))
BEGIN
    CREATE TABLE [dbo].[categories] (
        -- Các trường từ BaseEntity (TỰ ĐỘNG có khi extends BaseEntity)
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [created_at] DATETIME2 NOT NULL,
        [updated_at] DATETIME2 NULL,
        
        -- Các trường từ Category entity
        [name] NVARCHAR(255) NOT NULL,
        [description] NVARCHAR(MAX) NULL,
        [parent_category_id] BIGINT NULL,
        [is_active] BIT NOT NULL DEFAULT 1,
        
        CONSTRAINT [PK_categories] PRIMARY KEY CLUSTERED ([id] ASC),
        CONSTRAINT [UK_categories_name] UNIQUE NONCLUSTERED ([name] ASC),
        CONSTRAINT [FK_categories_parent] FOREIGN KEY ([parent_category_id]) 
            REFERENCES [dbo].[categories] ([id]) ON DELETE NO ACTION
    );
    
    CREATE NONCLUSTERED INDEX [IX_categories_created_at] ON [dbo].[categories] ([created_at] ASC);
    CREATE NONCLUSTERED INDEX [IX_categories_parent_id] ON [dbo].[categories] ([parent_category_id] ASC);
    PRINT 'Bảng categories đã được tạo thành công!';
END
ELSE
BEGIN
    PRINT 'Bảng categories đã tồn tại!';
END
GO

-- =============================================
-- THÊM DỮ LIỆU DEMO
-- =============================================

-- Demo data cho users
IF NOT EXISTS (SELECT 1 FROM [dbo].[users])
BEGIN
    INSERT INTO [dbo].[users] ([username], [email], [password], [first_name], [last_name], [enabled], [role], [created_at], [updated_at])
    VALUES
        (N'admin', N'admin@example.com', N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Admin', N'User', 1, N'ADMIN', GETDATE(), GETDATE()),
        (N'john_doe', N'john@example.com', N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'John', N'Doe', 1, N'USER', GETDATE(), GETDATE());
    PRINT 'Đã thêm dữ liệu demo cho users';
END
GO

-- Demo data cho categories
IF NOT EXISTS (SELECT 1 FROM [dbo].[categories])
BEGIN
    INSERT INTO [dbo].[categories] ([name], [description], [is_active], [created_at], [updated_at])
    VALUES
        (N'Electronics', N'Điện tử và công nghệ', 1, GETDATE(), GETDATE()),
        (N'Clothing', N'Quần áo và thời trang', 1, GETDATE(), GETDATE()),
        (N'Books', N'Sách và tài liệu', 1, GETDATE(), GETDATE());
    PRINT 'Đã thêm dữ liệu demo cho categories';
END
GO

-- Demo data cho products
IF NOT EXISTS (SELECT 1 FROM [dbo].[products])
BEGIN
    INSERT INTO [dbo].[products] ([name], [description], [price], [stock_quantity], [category_id], [is_active], [created_at], [updated_at])
    VALUES
        (N'Laptop Dell XPS 15', N'Laptop cao cấp với màn hình 15 inch', 25000000, 10, 1, 1, GETDATE(), GETDATE()),
        (N'iPhone 15 Pro', N'Điện thoại thông minh mới nhất', 30000000, 20, 1, 1, GETDATE(), GETDATE()),
        (N'Áo thun nam', N'Áo thun cotton 100%', 200000, 50, 2, 1, GETDATE(), GETDATE());
    PRINT 'Đã thêm dữ liệu demo cho products';
END
GO

-- Demo data cho orders
IF NOT EXISTS (SELECT 1 FROM [dbo].[orders])
BEGIN
    INSERT INTO [dbo].[orders] ([user_id], [total_amount], [status], [shipping_address], [created_at], [updated_at])
    VALUES
        (2, 25000000, N'COMPLETED', N'123 Đường ABC, Quận 1, TP.HCM', GETDATE(), GETDATE()),
        (2, 200000, N'PENDING', N'456 Đường XYZ, Quận 2, TP.HCM', GETDATE(), GETDATE());
    PRINT 'Đã thêm dữ liệu demo cho orders';
END
GO

-- =============================================
-- KIỂM TRA DỮ LIỆU - Xem tất cả bảng đều có id, created_at, updated_at
-- =============================================

PRINT '';
PRINT '=== KIỂM TRA: Tất cả bảng đều có id, created_at, updated_at từ BaseEntity ===';
PRINT '';

PRINT '--- Bảng USERS ---';
SELECT TOP 2 [id], [username], [email], [created_at], [updated_at] FROM [dbo].[users];
PRINT '';

PRINT '--- Bảng PRODUCTS ---';
SELECT TOP 2 [id], [name], [price], [created_at], [updated_at] FROM [dbo].[products];
PRINT '';

PRINT '--- Bảng ORDERS ---';
SELECT TOP 2 [id], [user_id], [total_amount], [status], [created_at], [updated_at] FROM [dbo].[orders];
PRINT '';

PRINT '--- Bảng CATEGORIES ---';
SELECT TOP 2 [id], [name], [description], [created_at], [updated_at] FROM [dbo].[categories];
PRINT '';

-- =============================================
-- THỐNG KÊ: Xem cấu trúc các bảng
-- =============================================

PRINT '=== CẤU TRÚC CÁC BẢNG ===';
PRINT '';

-- Users
PRINT 'Bảng USERS:';
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'users'
ORDER BY ORDINAL_POSITION;
PRINT '';

-- Products
PRINT 'Bảng PRODUCTS:';
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'products'
ORDER BY ORDINAL_POSITION;
PRINT '';

-- Orders
PRINT 'Bảng ORDERS:';
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'orders'
ORDER BY ORDINAL_POSITION;
PRINT '';

-- Categories
PRINT 'Bảng CATEGORIES:';
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'categories'
ORDER BY ORDINAL_POSITION;
PRINT '';

-- =============================================
-- KẾT LUẬN
-- =============================================
PRINT '========================================';
PRINT 'KẾT LUẬN:';
PRINT 'Tất cả các bảng (users, products, orders, categories)';
PRINT 'đều có các cột từ BaseEntity: id, created_at, updated_at';
PRINT 'mà KHÔNG cần tạo bảng "base_entities" riêng!';
PRINT '========================================';
GO

