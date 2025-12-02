# Tổng quan mã nguồn SEP26 Management

Tài liệu này mô tả kiến trúc và chức năng của toàn bộ mã nguồn trong thư mục `src/` của dự án Spring Boot **SEP26 Management**. Ứng dụng tuân theo các nguyên tắc Clean Architecture với các tầng `presentation → application → domain → infrastructure`.

## 1. Khởi động & cấu hình chung

- `Sep26ManagementApplication.java` cấu hình `@SpringBootApplication` và bật JPA repository cho gói `infrastructure.persistence.jpa`, đóng vai trò điểm vào (entry point) của ứng dụng.
- `src/main/resources/application.properties` cấu hình H2 in-memory DB, Hibernate (`ddl-auto=update`, logging SQL), tham số server, JWT (`jwt.secret`, `jwt.expiration`) và đường dẫn Swagger UI.

## 2. Domain Layer (`domain`)

### 2.1 Entity
- `BaseEntity`: lớp cơ sở `@MappedSuperclass`, cung cấp `id`, `createdAt`, `updatedAt` cùng hook `@PrePersist/@PreUpdate`.
- `ExampleEntity`: ví dụ thực thể đơn giản với trường `name/description/status`.
- `User`: biểu diễn người dùng hệ thống với ràng buộc duy nhất trên `username` và `email`, trường hồ sơ (`firstName/lastName`), trạng thái `enabled` và `role`.

### 2.2 Repository Interface
- `ExampleRepository`: CRUD cơ bản cho `ExampleEntity` (chưa có triển khai cụ thể).
- `UserRepository`: hợp đồng truy cập `User` (lưu, tìm theo id/username/email, kiểm tra tồn tại). Đây là cổng domain để tách biệt persistence thực tế.

### 2.3 Exception
- `DomainException`: gốc cho các ngoại lệ nghiệp vụ.
- `AuthenticationException`, `InvalidCredentialsException`: dành cho các lỗi xác thực/JWT.
- `EntityNotFoundException`: thông báo không tìm thấy thực thể với thông điệp được định dạng sẵn.

## 3. Application Layer (`application`)

### 3.1 DTO
- `LoginRequest`, `RegisterRequest`: dữ liệu vào với ràng buộc `jakarta.validation`.
- `LoginResponse`: trả JWT + thông tin người dùng.
- `UserDto`: đại diện dữ liệu người dùng trả về presentation.

### 3.2 Mapper
- `UserMapper`: chuyển đổi hai chiều giữa `User` và `UserDto`.

### 3.3 Port & Service
- `AuthUseCase`: cổng (port) định nghĩa các hành động `login`, `register`, `getCurrentUser`.
- `AuthService`: hiện thực use case với `@Transactional`.
  - `login()`: tìm user theo username, kiểm tra mật khẩu qua `PasswordEncoderService`, trạng thái `enabled`, tạo JWT bằng `JwtTokenProvider` và trả `LoginResponse`.
  - `register()`: đảm bảo uniqueness của username/email, mã hóa mật khẩu, lưu `UserRepository` và trả `UserDto`.
  - `getCurrentUser()`: tách token, kiểm tra hợp lệ JWT, trích suất username, truy vấn user và ánh xạ sang `UserDto`.

## 4. Infrastructure Layer (`infrastructure`)

### 4.1 Persistence
- `UserJpaRepository`: extends `JpaRepository<User, Long>` với truy vấn theo username/email.
- `UserRepositoryImpl`: “adapter” hiện thực `domain.repository.UserRepository` bằng cách ủy thác cho `UserJpaRepository`.

### 4.2 Security
- `PasswordEncoderService` + `PasswordEncoderImpl`: đóng gói `BCryptPasswordEncoder` để mã hóa/kiểm tra mật khẩu.
- `JwtTokenProvider`: tạo, đọc và xác thực JWT bằng `io.jsonwebtoken` (HS512), nhúng claim `role`.
- `JwtAuthenticationFilter`: filter `OncePerRequestFilter` đọc header `Authorization`, xác thực token, dựng `UsernamePasswordAuthenticationToken` với role (`ROLE_<role>`) rồi đặt vào `SecurityContext`.

### 4.3 Configuration
- `SecurityConfig`: cấu hình Spring Security (tắt CSRF, bật CORS, session stateless), whitelist các endpoint công khai (`/api/auth/**`, `/api/health`, Swagger, H2), thêm `JwtAuthenticationFilter` trước `UsernamePasswordAuthenticationFilter`, cho phép `frameOptions` sameOrigin để dùng H2 console.
- `OpenApiConfig`: cấu hình Swagger/OpenAPI (metadata, server URL, security scheme bearer JWT).

### 4.4 Exception Handling
- `GlobalExceptionHandler`: `@RestControllerAdvice` gom ngoại lệ domain và trả JSON chuẩn hóa (timestamp, status, error, message) cho `EntityNotFound`, `InvalidCredentials`, `Authentication`, `Domain`, và `Exception` chung.

## 5. Presentation Layer (`presentation`)

- `AuthController` (`/api/auth`):
  - `POST /login`: nhận `LoginRequest`, gọi `authUseCase.login`.
  - `POST /register`: gọi `authUseCase.register`, trả `201 Created`.
  - `GET /me`: yêu cầu header `Authorization`, xác thực theo `SecurityRequirement` bearer, trả `UserDto`.
  Các endpoint đều có mô tả Swagger (`@Operation`, `@ApiResponses`).
- `HealthController` (`/api/health`): endpoint GET đơn giản trả trạng thái `"UP"` dùng kiểm tra health và hiển thị trên Swagger.

## 6. Kiểm thử (`test`)

- `Sep26ManagementApplicationTests`: lớp thử nghiệm mặc định Spring Boot (hiện tại không có test cụ thể, chỉ đảm bảo context load).

## 7. Luồng yêu cầu điển hình

1. **Đăng ký**: client gửi `POST /api/auth/register` → controller xác thực body → `AuthService.register` → `UserRepositoryImpl` lưu qua JPA → trả về `UserDto`.
2. **Đăng nhập**: client gửi `POST /api/auth/login` → `AuthService.login` kiểm `BCrypt`, trạng thái enabled → tạo JWT → trả `LoginResponse`.
3. **Request bảo vệ**: client gửi JWT trong header `Authorization` → `JwtAuthenticationFilter` xác thực → request chảy xuống controller/business logic với `SecurityContext` đã chứa principal và role.
4. **Lỗi nghiệp vụ**: mọi ngoại lệ domain được `GlobalExceptionHandler` biến thành JSON thống nhất cho client.

## 8. Công nghệ chính

- Spring Boot 3, Spring Web, Spring Security, Spring Data JPA.
- CSDL H2 in-memory (có thể thay thế bằng SQL khác qua cấu hình).
- JWT (JJwt), BCrypt, Swagger/OpenAPI 3, Lombok.

Tài liệu này giúp hiểu nhanh từng lớp và vai trò của chúng khi cần mở rộng tính năng hoặc tích hợp thêm module mới trong dự án.

