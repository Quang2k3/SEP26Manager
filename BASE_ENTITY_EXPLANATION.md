# Giải Thích Luồng Xử Lý BaseEntity

## 📋 Tổng Quan

`BaseEntity` là một **abstract class** sử dụng annotation `@MappedSuperclass` trong JPA/Hibernate. Nó KHÔNG tạo bảng riêng trong database, mà chỉ cung cấp các trường và logic chung cho các entity khác kế thừa.

---

## 🔍 BaseEntity Làm Gì?

### 1. **Cung Cấp Các Trường Chung (Common Fields)**
```java
- id: Long (Primary Key, tự động tăng)
- createdAt: LocalDateTime (Thời gian tạo, không thể sửa)
- updatedAt: LocalDateTime (Thời gian cập nhật)
```

### 2. **Tự Động Quản Lý Timestamps**
- `@PrePersist`: Tự động set `createdAt` và `updatedAt` khi INSERT
- `@PreUpdate`: Tự động cập nhật `updatedAt` khi UPDATE

### 3. **Pattern: Code Reuse (Tái Sử Dụng Code)**
Thay vì phải viết lại `id`, `createdAt`, `updatedAt` cho mỗi entity, bạn chỉ cần:
```java
public class User extends BaseEntity {
    // Chỉ cần khai báo các trường riêng của User
    // id, createdAt, updatedAt đã có sẵn từ BaseEntity
}
```

---

## 🏗️ Cách Hoạt Động: @MappedSuperclass

### ❌ KHÔNG Tạo Bảng Riêng
```
BaseEntity (@MappedSuperclass)
    ↓
    KHÔNG tạo bảng "base_entities" trong database
```

### ✅ Các Trường Được "Copy" Vào Bảng Con
```
User extends BaseEntity
    ↓
    Tạo bảng "users" với TẤT CẢ các trường:
    - id (từ BaseEntity)
    - created_at (từ BaseEntity)
    - updated_at (từ BaseEntity)
    - username (từ User)
    - email (từ User)
    - password (từ User)
    - ... (các trường khác từ User)
```

---

## 🔄 Luồng Xử Lý Khi Lưu Dữ Liệu

### Khi INSERT một User mới:

1. **Bước 1: Tạo Object**
   ```java
   User user = User.builder()
       .username("john")
       .email("john@example.com")
       .password("hashed_password")
       .build();
   // id, createdAt, updatedAt = null (chưa set)
   ```

2. **Bước 2: Gọi save()**
   ```java
   userRepository.save(user);
   ```

3. **Bước 3: JPA Gọi @PrePersist Hook**
   ```java
   // BaseEntity.onCreate() được tự động gọi
   createdAt = LocalDateTime.now();  // Set thời gian hiện tại
   updatedAt = LocalDateTime.now();  // Set thời gian hiện tại
   ```

4. **Bước 4: JPA Tạo SQL INSERT**
   ```sql
   INSERT INTO users (username, email, password, created_at, updated_at)
   VALUES ('john', 'john@example.com', 'hashed_password', '2024-01-15 10:30:00', '2024-01-15 10:30:00');
   -- id được tự động generate bởi IDENTITY
   ```

5. **Bước 5: Database Trả Về ID**
   ```java
   // JPA tự động set id vào object
   user.getId(); // → 1 (ví dụ)
   ```

### Khi UPDATE một User:

1. **Bước 1: Load User từ Database**
   ```java
   User user = userRepository.findById(1L).get();
   ```

2. **Bước 2: Thay Đổi Dữ Liệu**
   ```java
   user.setEmail("newemail@example.com");
   ```

3. **Bước 3: Gọi save()**
   ```java
   userRepository.save(user);
   ```

4. **Bước 4: JPA Gọi @PreUpdate Hook**
   ```java
   // BaseEntity.onUpdate() được tự động gọi
   updatedAt = LocalDateTime.now();  // Cập nhật thời gian
   // createdAt KHÔNG thay đổi (updatable = false)
   ```

5. **Bước 5: JPA Tạo SQL UPDATE**
   ```sql
   UPDATE users 
   SET email = 'newemail@example.com', 
       updated_at = '2024-01-15 11:45:00'
   WHERE id = 1;
   -- created_at KHÔNG được update
   ```

---

## ❓ Tại Sao KHÔNG Cần Bảng Riêng?

### So Sánh 2 Cách:

#### ❌ Cách 1: Tạo Bảng Riêng (KHÔNG dùng @MappedSuperclass)
```
Bảng: base_entities
- id
- created_at
- updated_at

Bảng: users
- id (FK → base_entities.id)
- username
- email
- password
```

**Nhược điểm:**
- Phải JOIN 2 bảng mỗi khi query
- Phức tạp hơn, nhiều bảng hơn
- Performance kém hơn
- Phải quản lý Foreign Key

#### ✅ Cách 2: @MappedSuperclass (Cách hiện tại)
```
Bảng: users
- id (từ BaseEntity)
- created_at (từ BaseEntity)
- updated_at (từ BaseEntity)
- username (từ User)
- email (từ User)
- password (từ User)
```

**Ưu điểm:**
- Chỉ 1 bảng, không cần JOIN
- Đơn giản, dễ quản lý
- Performance tốt hơn
- Code gọn gàng, tái sử dụng được

---

## 🎯 Kết Luận

1. **BaseEntity KHÔNG tạo bảng riêng** - nó chỉ là template cho các entity khác
2. **Các trường của BaseEntity được "copy" vào bảng của entity con**
3. **Chỉ cần 1 bảng `users`** - đã bao gồm tất cả các trường từ BaseEntity và User
4. **Timestamps được tự động quản lý** - không cần set thủ công
5. **Pattern này giúp code DRY (Don't Repeat Yourself)** - tránh lặp lại code

---

## 📊 Sơ Đồ Luồng

```
┌─────────────────────────────────────┐
│      BaseEntity (@MappedSuperclass) │
│  - id: Long                         │
│  - createdAt: LocalDateTime         │
│  - updatedAt: LocalDateTime         │
│  - @PrePersist: onCreate()          │
│  - @PreUpdate: onUpdate()           │
└──────────────┬──────────────────────┘
               │ extends
               ↓
┌─────────────────────────────────────┐
│         User (@Entity)              │
│  - username: String                 │
│  - email: String                    │
│  - password: String                 │
│  - firstName: String                │
│  - lastName: String                 │
│  - enabled: Boolean                 │
│  - role: String                     │
└──────────────┬──────────────────────┘
               │
               ↓ JPA Mapping
               ↓
┌─────────────────────────────────────┐
│    Bảng: users (SQL Server)        │
│  - id (PK, IDENTITY)                │
│  - created_at (NOT NULL)            │
│  - updated_at                       │
│  - username (UNIQUE, NOT NULL)      │
│  - email (UNIQUE, NOT NULL)         │
│  - password (NOT NULL)              │
│  - first_name                       │
│  - last_name                        │
│  - enabled (NOT NULL, DEFAULT 1)    │
│  - role (NOT NULL, DEFAULT 'USER')  │
└─────────────────────────────────────┘
```

---

## ✅ Kết Luận Cuối Cùng

**BaseEntity KHÔNG cần tạo bảng riêng và liên kết với các bảng khác.**

Nó chỉ là một class template để các entity khác kế thừa, giúp:
- Tránh lặp lại code
- Tự động quản lý timestamps
- Đảm bảo tất cả entity đều có id, createdAt, updatedAt

**Chỉ cần tạo 1 bảng `users` với đầy đủ các cột từ cả BaseEntity và User.**

