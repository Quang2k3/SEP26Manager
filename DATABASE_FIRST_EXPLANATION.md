# Database First vs Code First - Giải Thích Về BaseEntity

## ⚠️ QUAN TRỌNG: Hiểu Nhầm Cần Làm Rõ

**Bạn đang hiểu SAI!** Trong Database First, bạn **VẪN PHẢI** tạo cột `id` trong database.

---

## 🔍 Database First là gì?

### Database First Approach:
```
1. Tạo Database trước (SQL Server)
   ↓
2. Viết Code Java để map với Database đã có
```

### Code First Approach:
```
1. Viết Code Java trước
   ↓
2. JPA tự động tạo Database từ Code
```

---

## 📊 So Sánh 2 Cách

### ❌ Code First (ddl-auto=update/create)
```properties
spring.jpa.hibernate.ddl-auto=update
```
- JPA tự động tạo/sửa bảng từ code
- Bạn chỉ cần viết Entity, JPA sẽ tạo bảng
- **KHÔNG cần** tạo bảng thủ công

### ✅ Database First (ddl-auto=none) - Cách bạn đang dùng
```properties
spring.jpa.hibernate.ddl-auto=none
```
- **BẠN PHẢI** tạo bảng trong SQL Server trước
- Code Java chỉ để **map** với bảng đã có
- JPA **KHÔNG tự động** tạo bảng

---

## 🎯 BaseEntity.java Làm Gì?

### BaseEntity.java CHỈ là Code Java:
```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // ← Chỉ là code Java
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;  // ← Chỉ là code Java
}
```

### BaseEntity.java KHÔNG tự động tạo cột trong Database!

**BaseEntity chỉ:**
1. ✅ Map field `id` → cột `id` trong database (nếu có)
2. ✅ Map field `createdAt` → cột `created_at` trong database (nếu có)
3. ✅ Tự động set timestamps khi INSERT/UPDATE
4. ❌ **KHÔNG** tạo cột trong database

---

## ⚠️ Điều Gì Xảy Ra Nếu Bạn KHÔNG Tạo Cột `id`?

### Nếu bảng `users` KHÔNG có cột `id`:

```sql
-- Bảng users thiếu cột id
CREATE TABLE users (
    username NVARCHAR(255),
    email NVARCHAR(255)
    -- THIẾU: id, created_at, updated_at
);
```

### Khi chạy ứng dụng:

```
❌ LỖI: JPA không tìm thấy cột "id" trong bảng "users"
❌ LỖI: Cannot map field "id" to column "id" - column does not exist
```

---

## ✅ Cách Đúng: Database First với BaseEntity

### Bước 1: Tạo Bảng trong SQL Server (PHẢI có cột `id`)

```sql
CREATE TABLE [dbo].[users] (
    -- PHẢI có các cột này (từ BaseEntity)
    [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    [created_at] DATETIME2 NOT NULL,
    [updated_at] DATETIME2 NULL,
    
    -- Các cột từ User entity
    [username] NVARCHAR(255) NOT NULL,
    [email] NVARCHAR(255) NOT NULL,
    [password] NVARCHAR(255) NOT NULL
);
```

### Bước 2: Code Java Map với Database

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    // BaseEntity đã có: id, createdAt, updatedAt
    // Code này map với các cột đã có trong database
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "email")
    private String email;
}
```

### Kết Quả:
- ✅ Database có cột `id` → Code map với cột đó
- ✅ Database có cột `created_at` → Code map với cột đó
- ✅ Mọi thứ hoạt động bình thường

---

## 🔄 Luồng Hoạt Động: Database First

```
┌─────────────────────────────────────┐
│  1. TẠO DATABASE (SQL Server)       │
│     CREATE TABLE users (            │
│       id BIGINT IDENTITY(1,1),      │ ← PHẢI có
│       created_at DATETIME2,         │ ← PHẢI có
│       updated_at DATETIME2,         │ ← PHẢI có
│       username NVARCHAR(255)        │
│     )                               │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│  2. VIẾT CODE JAVA                  │
│     BaseEntity {                    │
│       Long id;                      │ ← Map với cột "id"
│       LocalDateTime createdAt;      │ ← Map với cột "created_at"
│     }                               │
│                                     │
│     User extends BaseEntity {       │
│       String username;              │ ← Map với cột "username"
│     }                               │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│  3. JPA MAP CODE → DATABASE         │
│     - id → cột "id"                 │
│     - createdAt → cột "created_at"  │
│     - username → cột "username"     │
└─────────────────────────────────────┘
```

---

## 📝 Ví Dụ Cụ Thể

### Scenario: Bạn muốn tạo bảng `products`

#### ❌ SAI: Không tạo cột `id` trong database
```sql
CREATE TABLE products (
    name NVARCHAR(255),
    price DECIMAL(18,2)
    -- THIẾU id, created_at, updated_at
);
```

```java
@Entity
public class Product extends BaseEntity {
    // BaseEntity có id, nhưng database KHÔNG có cột id
    // → LỖI khi chạy!
}
```

#### ✅ ĐÚNG: Tạo đầy đủ các cột (bao gồm từ BaseEntity)
```sql
CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,  -- ← PHẢI có
    created_at DATETIME2 NOT NULL,        -- ← PHẢI có
    updated_at DATETIME2 NULL,            -- ← PHẢI có
    name NVARCHAR(255),
    price DECIMAL(18,2)
);
```

```java
@Entity
@Table(name = "products")
public class Product extends BaseEntity {
    // BaseEntity map với: id, created_at, updated_at
    // Product map với: name, price
    // → Hoạt động tốt!
}
```

---

## 🎯 Kết Luận

### Trong Database First:

1. ✅ **PHẢI** tạo cột `id` trong database
2. ✅ **PHẢI** tạo cột `created_at` trong database
3. ✅ **PHẢI** tạo cột `updated_at` trong database
4. ✅ BaseEntity.java chỉ để **map** với các cột đã có
5. ❌ BaseEntity.java **KHÔNG tự động** tạo cột

### BaseEntity.java giúp:
- ✅ Tránh lặp lại code (không cần viết id, createdAt, updatedAt cho mỗi entity)
- ✅ Tự động quản lý timestamps (@PrePersist, @PreUpdate)
- ✅ Đảm bảo tất cả entity đều có cấu trúc giống nhau

### Nhưng bạn vẫn phải:
- ✅ Tạo các cột đó trong database trước
- ✅ Đảm bảo tên cột khớp với `@Column(name = "...")`

---

## 📋 Checklist Khi Tạo Bảng Mới (Database First)

Khi tạo bảng mới trong SQL Server, **LUÔN** phải có:

- [ ] `id` BIGINT IDENTITY(1,1) PRIMARY KEY
- [ ] `created_at` DATETIME2 NOT NULL
- [ ] `updated_at` DATETIME2 NULL
- [ ] Các cột riêng của entity đó

Sau đó mới viết Entity Java extends BaseEntity để map với bảng đó.

