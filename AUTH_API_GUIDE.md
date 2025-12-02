# Hướng Dẫn Sử Dụng API Authentication

## 📋 Tổng Quan

Hệ thống authentication sử dụng **JWT (JSON Web Token)** với các endpoint sau:

- `POST /api/auth/register` - Đăng ký tài khoản mới
- `POST /api/auth/login` - Đăng nhập và nhận JWT token
- `GET /api/auth/me` - Lấy thông tin user hiện tại (yêu cầu authentication)

## 🔐 API Endpoints

### 1. Đăng Ký (Register)

**Endpoint:** `POST /api/auth/register`

**Request Body:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "role": "USER",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

**Validation Rules:**
- `username`: Required, 3-20 characters, unique
- `email`: Required, valid email format, unique
- `password`: Required, minimum 6 characters
- `firstName`, `lastName`: Optional

**Error Responses:**
- `400 Bad Request`: Username or email already exists
- `400 Bad Request`: Validation errors

---

### 2. Đăng Nhập (Login)

**Endpoint:** `POST /api/auth/login`

**Request Body:**
```json
{
  "username": "john_doe",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huX2RvZSIsInJvbGUiOiJVU0VSIiwiaWF0IjoxNzA1Mjc1ODAwLCJleHAiOjE3MDUzNjIyMDB9...",
  "type": "Bearer",
  "user": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "enabled": true,
    "role": "USER",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid username or password
- `401 Unauthorized`: Account is disabled
- `400 Bad Request`: Validation errors

---

### 3. Lấy Thông Tin User Hiện Tại

**Endpoint:** `GET /api/auth/me`

**Headers:**
```
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "role": "USER",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing token
- `401 Unauthorized`: Token expired

---

## 🔒 Security

### JWT Token

- **Token Type:** Bearer Token
- **Algorithm:** HS512 (HMAC with SHA-512)
- **Default Expiration:** 24 hours (86400000 ms)
- **Config:** Có thể cấu hình trong `application.properties`

### Protected Endpoints

Tất cả endpoints ngoại trừ `/api/auth/**` và `/api/health` đều yêu cầu authentication.

**Public Endpoints:**
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/health`

**Protected Endpoints:**
- `GET /api/auth/me` (yêu cầu token)
- Tất cả endpoints khác trong hệ thống

---

## 📝 Ví Dụ Sử Dụng với cURL

### 1. Đăng ký user mới:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }'
```

### 2. Đăng nhập:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

**Lưu token từ response:**
```bash
TOKEN="eyJhbGciOiJIUzUxMiJ9..."
```

### 3. Lấy thông tin user hiện tại:
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## 📝 Ví Dụ Sử Dụng với Postman

### 1. Đăng ký:
- Method: `POST`
- URL: `http://localhost:8080/api/auth/register`
- Headers: `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "password123",
  "firstName": "Test",
  "lastName": "User"
}
```

### 2. Đăng nhập:
- Method: `POST`
- URL: `http://localhost:8080/api/auth/login`
- Headers: `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "username": "testuser",
  "password": "password123"
}
```
- Copy token từ response

### 3. Lấy thông tin user:
- Method: `GET`
- URL: `http://localhost:8080/api/auth/me`
- Headers: 
  - `Authorization: Bearer <paste_token_here>`

---

## ⚙️ Configuration

### JWT Configuration (application.properties)

```properties
# JWT Secret Key (nên sử dụng key dài ít nhất 256 bits cho HS512)
jwt.secret=MySecretKeyForJWTTokenGenerationMustBeAtLeast256BitsLongForHS512AlgorithmSecurity

# JWT Expiration (milliseconds)
jwt.expiration=86400000  # 24 hours
```

### Security Configuration

- **CORS:** Enabled for all origins (có thể cấu hình lại trong `SecurityConfig`)
- **Session:** Stateless (không sử dụng session)
- **CSRF:** Disabled (vì dùng JWT)

---

## 🔍 Flow Authentication

```
1. User đăng ký → POST /api/auth/register
   ↓
2. User đăng nhập → POST /api/auth/login
   ↓
3. Server trả về JWT token
   ↓
4. Client lưu token (localStorage, cookie, etc.)
   ↓
5. Client gửi token trong header: Authorization: Bearer <token>
   ↓
6. JWT Filter validate token → Extract username & role
   ↓
7. Spring Security set authentication context
   ↓
8. Controller xử lý request
```

---

## 🛠️ Troubleshooting

### Lỗi 401 Unauthorized:
- Kiểm tra token có đúng format không: `Bearer <token>`
- Kiểm tra token có hết hạn không
- Kiểm tra token có bị sửa đổi không

### Lỗi 400 Bad Request khi đăng ký:
- Username hoặc email đã tồn tại
- Validation errors (password quá ngắn, email không đúng format, etc.)

### Token không hoạt động:
- Đảm bảo đã gửi token trong header `Authorization`
- Format đúng: `Bearer <token>` (có khoảng trắng sau Bearer)
- Token chưa hết hạn

---

## 🚀 Testing

### Test Script (Bash):

```bash
#!/bin/bash

BASE_URL="http://localhost:8080/api/auth"

# 1. Register
echo "Registering user..."
REGISTER_RESPONSE=$(curl -s -X POST $BASE_URL/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }')

echo "Register Response: $REGISTER_RESPONSE"

# 2. Login
echo -e "\nLogging in..."
LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }')

echo "Login Response: $LOGIN_RESPONSE"

# Extract token
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
echo -e "\nToken: $TOKEN"

# 3. Get current user
echo -e "\nGetting current user..."
curl -X GET $BASE_URL/me \
  -H "Authorization: Bearer $TOKEN"
```

---

**Chúc bạn sử dụng thành công!** 🎉

