# Hướng Dẫn Sử Dụng Swagger UI

## 📋 Tổng Quan

Dự án đã được tích hợp **Swagger UI** (SpringDoc OpenAPI) để test và document API một cách trực quan.

## 🚀 Truy Cập Swagger UI

Sau khi khởi chạy ứng dụng, truy cập Swagger UI tại:

**URL:** http://localhost:8080/swagger-ui.html

Hoặc:

**URL:** http://localhost:8080/swagger-ui/index.html

**API Documentation (JSON):** http://localhost:8080/v3/api-docs

---

## 📚 Các Endpoints Trong Swagger

### 1. **Authentication** (Tag: Authentication)

#### 🔵 POST `/api/auth/login`
- **Mô tả:** Đăng nhập và nhận JWT token
- **Authentication:** Không cần
- **Request Body:**
  ```json
  {
    "username": "testuser",
    "password": "password123"
  }
  ```
- **Response:** LoginResponse với token và user info

#### 🔵 POST `/api/auth/register`
- **Mô tả:** Đăng ký user mới
- **Authentication:** Không cần
- **Request Body:**
  ```json
  {
    "username": "newuser",
    "email": "newuser@example.com",
    "password": "password123",
    "firstName": "New",
    "lastName": "User"
  }
  ```
- **Response:** UserDto với thông tin user đã tạo

#### 🔒 GET `/api/auth/me`
- **Mô tả:** Lấy thông tin user hiện tại
- **Authentication:** ✅ **Cần JWT token**
- **Headers:** Authorization: Bearer `<token>`
- **Response:** UserDto

### 2. **Health** (Tag: Health)

#### 🔵 GET `/api/health`
- **Mô tả:** Health check endpoint
- **Authentication:** Không cần
- **Response:** Status và message

---

## 🔐 Cách Sử Dụng JWT Authentication trong Swagger

### Bước 1: Đăng nhập để lấy Token

1. Mở Swagger UI: http://localhost:8080/swagger-ui.html
2. Tìm endpoint **POST `/api/auth/login`**
3. Click vào để mở rộng
4. Click nút **"Try it out"**
5. Điền thông tin:
   ```json
   {
     "username": "testuser",
     "password": "password123"
   }
   ```
6. Click **"Execute"**
7. Copy token từ response (trong field `token`)

### Bước 2: Authorize với Token

1. Tìm nút **"Authorize"** ở góc trên bên phải của Swagger UI
2. Click vào nút **"Authorize"**
3. Trong popup, tìm section **"bearerAuth"**
4. Nhập token vào ô **"Value"** theo format:
   ```
   Bearer <your_token_here>
   ```
   Ví dụ:
   ```
   Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsInJvbGUiOiJVU0VSIiwiaWF0IjoxNzA1Mjc1ODAwLCJleHAiOjE3MDUzNjIyMDB9...
   ```
5. Click **"Authorize"**
6. Click **"Close"**

### Bước 3: Test Protected Endpoints

Sau khi authorize:
- Tất cả protected endpoints sẽ tự động gửi token trong header
- Test endpoint `GET /api/auth/me` để xác nhận authentication hoạt động

---

## 📖 Hướng Dẫn Chi Tiết Sử Dụng Swagger UI

### 1. Đăng Ký User Mới

1. **Tìm endpoint:** `POST /api/auth/register`
2. **Click "Try it out"**
3. **Điền Request Body:**
   ```json
   {
     "username": "swaggeruser",
     "email": "swagger@example.com",
     "password": "swagger123",
     "firstName": "Swagger",
     "lastName": "User"
   }
   ```
4. **Click "Execute"**
5. **Kiểm tra Response:**
   - Status: `201 Created`
   - Body chứa thông tin user đã tạo

### 2. Đăng Nhập

1. **Tìm endpoint:** `POST /api/auth/login`
2. **Click "Try it out"**
3. **Điền Request Body:**
   ```json
   {
     "username": "swaggeruser",
     "password": "swagger123"
   }
   ```
4. **Click "Execute"**
5. **Copy token** từ response:
   ```json
   {
     "token": "eyJhbGciOiJIUzUxMiJ9...",
     "type": "Bearer",
     "user": { ... }
   }
   ```

### 3. Authorize với Token

1. **Click nút "Authorize"** (🔒) ở góc trên bên phải
2. **Trong popup:**
   - Section: **"bearerAuth"**
   - Value: `Bearer <paste_token_here>`
   - Click **"Authorize"**
   - Click **"Close"**

### 4. Test Protected Endpoint

1. **Tìm endpoint:** `GET /api/auth/me`
2. **Click "Try it out"**
3. **Token sẽ tự động được gửi** trong header (không cần điền thủ công)
4. **Click "Execute"**
5. **Kiểm tra Response:**
   - Status: `200 OK`
   - Body chứa thông tin user hiện tại

---

## 🎨 Tính Năng Swagger UI

### 1. **Filter và Search**
- Sử dụng ô search để tìm endpoints
- Sắp xếp theo method hoặc tag

### 2. **View Models**
- Click vào các Schema definitions để xem cấu trúc DTOs
- Xem validation rules cho từng field

### 3. **Try It Out**
- Test trực tiếp API từ Swagger UI
- Xem request/response chi tiết
- View cURL command để copy

### 4. **Response Examples**
- Xem example responses
- Xem error responses

---

## 🔧 Cấu Hình Swagger

### Application Properties

```properties
# Swagger/OpenAPI Configuration
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.tryItOutEnabled=true
```

### Customization trong OpenApiConfig

- **API Title:** SEP26 Management API
- **Version:** 1.0.0
- **Contact:** Support team email
- **License:** Apache 2.0
- **Security Scheme:** Bearer JWT

---

## 📝 Ví Dụ Workflow Test

### Complete Testing Flow:

```
1. Mở Swagger UI: http://localhost:8080/swagger-ui.html
   ↓
2. Đăng ký user mới
   POST /api/auth/register
   {
     "username": "testuser",
     "email": "test@example.com",
     "password": "password123"
   }
   ↓
3. Đăng nhập để lấy token
   POST /api/auth/login
   {
     "username": "testuser",
     "password": "password123"
   }
   → Copy token từ response
   ↓
4. Authorize với token
   Click "Authorize" → Nhập: Bearer <token>
   ↓
5. Test protected endpoint
   GET /api/auth/me
   → Xem thông tin user hiện tại
```

---

## 🛠️ Troubleshooting

### Swagger UI không hiển thị:
- ✅ Kiểm tra ứng dụng đã chạy chưa
- ✅ Truy cập đúng URL: http://localhost:8080/swagger-ui.html
- ✅ Kiểm tra SecurityConfig đã allow `/swagger-ui/**`

### Không thể authorize:
- ✅ Đảm bảo format token: `Bearer <token>` (có khoảng trắng sau Bearer)
- ✅ Token chưa hết hạn
- ✅ Token được copy đầy đủ (không bị cắt)

### Protected endpoints trả về 401:
- ✅ Đã click "Authorize" và nhập token chưa
- ✅ Format token đúng: `Bearer <token>`
- ✅ Token còn hiệu lực

### Error khi test endpoints:
- ✅ Kiểm tra request body format (JSON valid)
- ✅ Kiểm tra validation rules (username length, email format, etc.)
- ✅ Kiểm tra server logs để xem chi tiết lỗi

---

## 📸 Screenshots Mô Tả

### Swagger UI Homepage
- Hiển thị tất cả APIs được nhóm theo tags
- Có nút "Authorize" ở góc trên bên phải

### Try It Out
- Click "Try it out" để enable editing
- Điền request body
- Click "Execute" để gửi request
- Xem response ngay bên dưới

### Authorize Dialog
- Popup hiển thị khi click "Authorize"
- Section "bearerAuth" để nhập JWT token
- Format: `Bearer <token>`

### Response View
- Status code (200, 201, 400, 401, etc.)
- Response headers
- Response body (formatted JSON)
- CURL command để copy

---

## 🎯 Best Practices

1. **Luôn test login trước** để lấy token
2. **Authorize ngay sau khi có token** để test protected endpoints
3. **Xem Schema definitions** để hiểu cấu trúc request/response
4. **Sử dụng cURL command** để test từ command line nếu cần
5. **Check response status** để xác nhận request thành công

---

## 🔗 Useful Links

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **OpenAPI YAML:** http://localhost:8080/v3/api-docs.yaml

---

**Chúc bạn test API thành công với Swagger UI!** 🎉

