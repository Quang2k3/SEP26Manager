# Ví Dụ: BaseEntity với Nhiều Bảng

## 🎯 Câu Trả Lời Ngắn Gọn

**KHÔNG cần làm gì thêm!** Chỉ cần cho entity mới `extends BaseEntity` là xong.

---

## 📝 Ví Dụ Thực Tế

### Giả sử bạn có các entity sau:

#### 1. User (đã có)
```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    private String username;
    private String email;
    // ... các trường khác
    // Tự động có: id, createdAt, updatedAt từ BaseEntity
}
```

#### 2. Product (entity mới)
```java
@Entity
@Table(name = "products")
public class Product extends BaseEntity {
    private String name;
    private BigDecimal price;
    private String description;
    // Chỉ cần khai báo các trường riêng
    // Tự động có: id, createdAt, updatedAt từ BaseEntity
}
```

#### 3. Order (entity mới)
```java
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    private Long userId;
    private BigDecimal totalAmount;
    private String status;
    // Chỉ cần khai báo các trường riêng
    // Tự động có: id, createdAt, updatedAt từ BaseEntity
}
```

#### 4. Category (entity mới)
```java
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {
    private String name;
    private String description;
    // Chỉ cần khai báo các trường riêng
    // Tự động có: id, createdAt, updatedAt từ BaseEntity
}
```

---

## 🗄️ Kết Quả Trong Database

Mỗi bảng sẽ **TỰ ĐỘNG** có các cột từ BaseEntity:

### Bảng `users`:
```
- id (từ BaseEntity)
- created_at (từ BaseEntity)
- updated_at (từ BaseEntity)
- username (từ User)
- email (từ User)
- password (từ User)
- ...
```

### Bảng `products`:
```
- id (từ BaseEntity)
- created_at (từ BaseEntity)
- updated_at (từ BaseEntity)
- name (từ Product)
- price (từ Product)
- description (từ Product)
```

### Bảng `orders`:
```
- id (từ BaseEntity)
- created_at (từ BaseEntity)
- updated_at (từ BaseEntity)
- user_id (từ Order)
- total_amount (từ Order)
- status (từ Order)
```

### Bảng `categories`:
```
- id (từ BaseEntity)
- created_at (từ BaseEntity)
- updated_at (từ BaseEntity)
- name (từ Category)
- description (từ Category)
```

---

## ✅ Lợi Ích

1. **Không cần viết lại code**: Mỗi entity chỉ cần `extends BaseEntity`
2. **Tự động quản lý timestamps**: `@PrePersist` và `@PreUpdate` hoạt động cho TẤT CẢ các entity
3. **Nhất quán**: Tất cả bảng đều có cùng cấu trúc cho id, created_at, updated_at
4. **Dễ bảo trì**: Nếu muốn thêm trường chung (ví dụ: `deleted_at`), chỉ cần sửa BaseEntity

---

## 🔄 Luồng Hoạt Động

```
BaseEntity (@MappedSuperclass)
    ├── id
    ├── createdAt
    ├── updatedAt
    ├── @PrePersist
    └── @PreUpdate
         │
         ├── User extends BaseEntity
         │    └── → Bảng: users (có id, created_at, updated_at + các trường User)
         │
         ├── Product extends BaseEntity
         │    └── → Bảng: products (có id, created_at, updated_at + các trường Product)
         │
         ├── Order extends BaseEntity
         │    └── → Bảng: orders (có id, created_at, updated_at + các trường Order)
         │
         └── Category extends BaseEntity
              └── → Bảng: categories (có id, created_at, updated_at + các trường Category)
```

---

## 📊 So Sánh

### ❌ Nếu KHÔNG dùng BaseEntity:

```java
// Phải viết lại cho mỗi entity
@Entity
public class User {
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // ... các trường khác
}

@Entity
public class Product {
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // ... các trường khác
}

// Lặp lại code nhiều lần!
```

### ✅ Với BaseEntity:

```java
// Chỉ cần extends
@Entity
public class User extends BaseEntity {
    // ... chỉ các trường riêng
}

@Entity
public class Product extends BaseEntity {
    // ... chỉ các trường riêng
}

// Code gọn gàng, không lặp lại!
```

---

## 🎯 Kết Luận

**Bạn KHÔNG cần làm gì thêm!**

- Chỉ cần cho entity mới `extends BaseEntity`
- JPA sẽ tự động tích hợp các trường vào bảng của entity đó
- Timestamps sẽ tự động được quản lý
- Mỗi bảng vẫn độc lập, không cần liên kết với bảng "base_entities"

**Đây chính là sức mạnh của pattern @MappedSuperclass!**

