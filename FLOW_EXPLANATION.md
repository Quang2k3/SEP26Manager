## Giải thích chi tiết luồng hoạt động trong module Authentication

File này mô tả chi tiết kiến trúc và luồng xử lý cho các chức năng chính:

- **Đăng nhập**: `POST /api/auth/login`
- **Đăng ký**: `POST /api/auth/register`
- **Lấy thông tin người dùng hiện tại**: `GET /api/auth/me`
- **Health check**: `GET /api/health`

Theo phong cách **Clean Architecture / Layered Architecture**, mã nguồn được chia thành các tầng chính:

- **Presentation (Web / REST)**: `presentation/controller/*`
- **Application (Use Case / Service)**: `application/service/*`, `application/port/*`, `application/dto/*`, `application/mapper/*`
- **Domain (Business Core)**: `domain/entity/*`, `domain/repository/*`, `domain/exception/*`
- **Infrastructure (Adapter / Framework Integration)**: `infrastructure/persistence/*`, `infrastructure/security/*`, `infrastructure/config/*`, `infrastructure/exception/*`

---

## 1. Tầng Presentation – Controller xử lý gì?

### 1.1. `AuthController` – Authentication API

**Vị trí**:  
`src/main/java/org/example/sep26management/presentation/controller/AuthController.java`

**Nhiệm vụ chính**:

- Nhận HTTP request cho các endpoint auth:
  - `POST /api/auth/login`
  - `POST /api/auth/register`
  - `GET /api/auth/me`
- Ánh xạ dữ liệu request vào DTO (`LoginRequest`, `RegisterRequest`).
- Kích hoạt validate input với `@Valid`.
- Gọi **use case interface** `AuthUseCase` (không phụ thuộc trực tiếp vào implementation).
- Đóng gói kết quả use case vào `ResponseEntity` và trả về client.

#### 1.1.1. Endpoint `/api/auth/login`

- **Method**: `POST /api/auth/login`
- **Hàm controller**:
  - Ký hiệu đơn giản:
    - Input: `LoginRequest`
    - Output: `LoginResponse`
    - Ủy quyền xử lý cho: `authUseCase.login(request)`

Vai trò:

- Controller **không chứa business logic** đăng nhập.
- Chỉ:
  - Nhận dữ liệu JSON từ body, map vào `LoginRequest`.
  - Gọi hàm của tầng application.
  - Trả về `200 OK` với `LoginResponse`.

#### 1.1.2. Endpoint `/api/auth/register`

- **Method**: `POST /api/auth/register`
- **Hàm controller**:
  - Input: `RegisterRequest`
  - Output: `UserDto`
  - Gọi: `authUseCase.register(request)`

Vai trò:

- Nhận thông tin đăng ký từ client, validate.
- Chuyển dữ liệu cho use case xử lý đăng ký.
- Trả về `201 Created` với thông tin user (`UserDto`).

#### 1.1.3. Endpoint `/api/auth/me`

- **Method**: `GET /api/auth/me`
- **Hàm controller**:
  - Input: header `Authorization: Bearer <token>`
  - Output: `UserDto` – thông tin user hiện tại
  - Gọi: `authUseCase.getCurrentUser(token)`

Vai trò:

- Đọc token từ header.
- Gọi use case để:
  - Xác thực token.
  - Lấy `username` từ token.
  - Lấy thông tin `User` từ DB và map sang `UserDto`.

> Lưu ý: Endpoint này được đánh dấu `@SecurityRequirement(name = "bearerAuth")` để Swagger biết cần JWT.

### 1.2. `HealthController` – Health Check

**Vị trí**:  
`src/main/java/org/example/sep26management/presentation/controller/HealthController.java`

**Endpoint**: `GET /api/health`

- Không gọi xuống service, không truy cập DB.
- Chỉ trả về một JSON đơn giản:
  - `status: "UP"`
  - `message: "Application is running"`

Đây là ví dụ về controller **chỉ xử lý HTTP** mà không đi qua business/service/repository.

---

## 2. Tầng Application – Use Case & Business Logic

Tầng này bao gồm:

- **Port (Interface)**: `AuthUseCase`
- **Service (Implementation)**: `AuthService`
- **DTO**: `LoginRequest`, `LoginResponse`, `RegisterRequest`, `UserDto`
- **Mapper**: `UserMapper`

### 2.1. `AuthUseCase` – Interface Use Case

**Vị trí**:  
`src/main/java/org/example/sep26management/application/port/AuthUseCase.java`

Khai báo 3 hành vi chính:

- `LoginResponse login(LoginRequest request);`
- `UserDto register(RegisterRequest request);`
- `UserDto getCurrentUser(String token);`

Controller **chỉ biết đến interface này**, không quan tâm triển khai cụ thể.

### 2.2. `AuthService` – Triển khai Use Case & Thuật toán nghiệp vụ

**Vị trí**:  
`src/main/java/org/example/sep26management/application/service/AuthService.java`

Đánh dấu:

- `@Service` – Spring bean tầng service.
- `@RequiredArgsConstructor` – Lombok tạo constructor cho các field final.
- `@Transactional` – tất cả method chạy trong transaction (mặc định read-write, trừ khi override).

**Dependencies (qua constructor)**:

- `UserRepository userRepository` – interface domain để làm việc với User.
- `UserMapper userMapper` – map Entity ↔ DTO.
- `JwtTokenProvider jwtTokenProvider` – sinh & validate JWT.
- `PasswordEncoderService passwordEncoder` – mã hóa & so sánh mật khẩu.

#### 2.2.1. Thuật toán đăng nhập – `login(LoginRequest request)`

Business logic chi tiết:

1. **Tìm user theo username**:
   - Gọi `userRepository.findByUsername(request.getUsername())`.
   - Nếu không tồn tại → ném `InvalidCredentialsException`.

2. **Kiểm tra mật khẩu**:
   - Dùng `passwordEncoder.matches(request.getPassword(), user.getPassword())`.
   - Nếu sai → ném `InvalidCredentialsException`.

3. **Kiểm tra trạng thái tài khoản**:
   - Nếu `user.getEnabled() == false` → ném `DomainException("Account is disabled")`.

4. **Sinh JWT token**:
   - Gọi `jwtTokenProvider.generateToken(user.getUsername(), user.getRole())`.
   - Token chứa:
     - `subject`: username
     - claim `"role"`: role của user
     - `issuedAt`, `expiration`

5. **Chuẩn bị response**:
   - Convert `User` → `UserDto` qua `userMapper.toDto(user)`.
   - Build `LoginResponse`:
     - `token`
     - `type = "Bearer"`
     - `user` (UserDto)

**Kết luận**:  
**Thuật toán login (xác thực, kiểm tra trạng thái, sinh token)** nằm trong **`AuthService`** – đây là phần **business logic chính** của use case đăng nhập.

#### 2.2.2. Thuật toán đăng ký – `register(RegisterRequest request)`

Luồng chi tiết:

1. **Check trùng username**:
   - `userRepository.existsByUsername(request.getUsername())`
   - Nếu `true` → ném `DomainException("Username already exists")`.

2. **Check trùng email**:
   - `userRepository.existsByEmail(request.getEmail())`
   - Nếu `true` → ném `DomainException("Email already exists")`.

3. **Mã hóa mật khẩu**:
   - `String encodedPassword = passwordEncoder.encode(request.getPassword());`
   - Sử dụng BCrypt phía dưới (trong `PasswordEncoderImpl`).

4. **Tạo entity `User` mới**:
   - Dùng builder:
     - `username`, `email`, `password` (đã mã hóa).
     - `firstName`, `lastName` (optional).
     - `enabled = true`.
     - `role = "USER"`.

5. **Lưu vào DB**:
   - `User saved = userRepository.save(user);`
   - Transactional sẽ đảm bảo việc ghi dữ liệu.

6. **Trả về DTO**:
   - `return userMapper.toDto(saved);`

**Kết luận**:  
Quy tắc nghiệp vụ về đăng ký (unique username/email, mã hóa password, set role mặc định) cũng được đặt trong **`AuthService`**.

#### 2.2.3. Thuật toán lấy user hiện tại – `getCurrentUser(String token)`

Luồng chi tiết:

1. **Xử lý tiền tố "Bearer "**:
   - Nếu header có dạng `"Bearer <token>"`, thì cắt đi `"Bearer "` và lấy phần token thuần.

2. **Validate token**:
   - Gọi `jwtTokenProvider.validateToken(token)`.
   - Nếu `false` → ném `InvalidCredentialsException("Invalid token")`.

3. **Lấy username từ token**:
   - `String username = jwtTokenProvider.getUsernameFromToken(token);`

4. **Truy vấn DB lấy User**:
   - `userRepository.findByUsername(username)`.
   - Nếu không có → `InvalidCredentialsException("User not found")`.

5. **Trả về DTO**:
   - `return userMapper.toDto(user);`

**Kết luận**:  
Nghiệp vụ xử lý token, tìm user hiện tại, mapping sang DTO đều nằm trong `AuthService`.

### 2.3. DTO – Dữ liệu vào/ra

#### 2.3.1. `LoginRequest`

- Field:
  - `username` – `@NotBlank`
  - `password` – `@NotBlank`
- Dùng cho `POST /api/auth/login`.
- Validate được kích hoạt bởi `@Valid` trong controller.

#### 2.3.2. `LoginResponse`

- Field:
  - `token` – JWT
  - `type` – mặc định `"Bearer"`
  - `user` – `UserDto`
- Trả về cho client sau khi login thành công.

#### 2.3.3. `RegisterRequest`

- Field:
  - `username`
    - `@NotBlank`
    - `@Size(min = 3, max = 20)`
  - `email`
    - `@NotBlank`
    - `@Email`
  - `password`
    - `@NotBlank`
    - `@Size(min = 6)`
  - `firstName`, `lastName` – optional.
- Dùng cho `POST /api/auth/register`.

#### 2.3.4. `UserDto`

- Field:
  - `id`, `username`, `email`, `firstName`, `lastName`, `enabled`, `role`
  - `createdAt`, `updatedAt`
- Dùng để expose thông tin user ra bên ngoài (không lộ password).

### 2.4. `UserMapper` – Chuyển đổi Entity ↔ DTO

**Vị trí**:  
`src/main/java/org/example/sep26management/application/mapper/UserMapper.java`

- `toDto(User user)`:
  - Dùng để convert entity `User` lấy từ DB → `UserDto` trả ra ngoài.
  - Copy các field: id, username, email, firstName, lastName, enabled, role, createdAt, updatedAt.
- `toEntity(UserDto dto)`:
  - Convert `UserDto` → `User` (ít được dùng hơn trong auth hiện tại).
  - Set cả `id`, `createdAt`, `updatedAt` nếu có.

---

## 3. Tầng Domain – Entity, Repository, Exception

### 3.1. Entity – `User` và `BaseEntity`

**`BaseEntity`**:  
`src/main/java/org/example/sep26management/domain/entity/BaseEntity.java`

- Chứa:
  - `id` – khóa chính, auto increment (`@GeneratedValue`).
  - `createdAt`, `updatedAt` – thời gian tạo & cập nhật.
- Có các hook:
  - `@PrePersist` → set `createdAt`, `updatedAt` trước khi insert.
  - `@PreUpdate` → update `updatedAt` trước khi update.

**`User`**:  
`src/main/java/org/example/sep26management/domain/entity/User.java`

- Table: `users` với constraint unique cho `username` và `email`.
- Field:
  - `username` – unique, not null.
  - `email` – unique, not null.
  - `password` – not null (lưu bản mã hóa).
  - `firstName`, `lastName`.
  - `enabled` – boolean, mặc định true.
  - `role` – string, mặc định `"USER"`.
- Kế thừa `BaseEntity` → có thêm `id`, `createdAt`, `updatedAt`.

### 3.2. Repository domain – `UserRepository`

**Vị trí**:  
`src/main/java/org/example/sep26management/domain/repository/UserRepository.java`

- Định nghĩa các hành vi cần cho domain:
  - `save(User user)`
  - `findById(Long id)`
  - `findByUsername(String username)`
  - `findByEmail(String email)`
  - `existsByUsername(String username)`
  - `existsByEmail(String email)`

Tầng application (`AuthService`) chỉ làm việc với **interface** này, không biết gì về JPA cụ thể.

### 3.3. Exception domain – `InvalidCredentialsException`, `DomainException`, ...

Ví dụ: `InvalidCredentialsException`:

- Kế thừa `AuthenticationException` trong domain.
- Dùng để biểu diễn lỗi sai username/password hoặc token không hợp lệ.
- Được sử dụng trong:
  - `login` – khi username không tồn tại hoặc password sai.
  - `getCurrentUser` – khi token invalid hoặc user không tồn tại.

Các exception domain thường sẽ được map sang HTTP status thích hợp trong `GlobalExceptionHandler` (ở tầng infrastructure).

---

## 4. Tầng Infrastructure – Truy xuất DB & Bảo mật

### 4.1. Truy xuất Database – `UserRepositoryImpl` & `UserJpaRepository`

#### 4.1.1. `UserJpaRepository` – Adapter JPA

**Vị trí**:  
`src/main/java/org/example/sep26management/infrastructure/persistence/jpa/UserJpaRepository.java`

- `extends JpaRepository<User, Long>`
- Khai báo thêm:
  - `Optional<User> findByUsername(String username);`
  - `Optional<User> findByEmail(String email);`
  - `boolean existsByUsername(String username);`
  - `boolean existsByEmail(String email);`

Spring Data JPA sẽ tự động tạo implementation, sinh SQL dựa trên tên hàm (`findBy*`, `existsBy*`).

#### 4.1.2. `UserRepositoryImpl` – Cầu nối Domain ↔ JPA

**Vị trí**:  
`src/main/java/org/example/sep26management/infrastructure/persistence/UserRepositoryImpl.java`

- Đánh dấu `@Repository`, `@RequiredArgsConstructor`.
- Implement `UserRepository`.
- Nội dung:
  - Giữ field `private final UserJpaRepository jpaRepository;`
  - Mỗi method domain repository được implement bằng cách delegate sang `jpaRepository`:
    - `save` → `jpaRepository.save(user)`
    - `findById` → `jpaRepository.findById(id)`
    - `findByUsername` → `jpaRepository.findByUsername(username)`
    - `existsByUsername` → `jpaRepository.existsByUsername(username)`
    - ...

**Kết luận**:  
**Truy xuất DB thực sự** nằm ở **`UserJpaRepository`** (qua Spring Data JPA), nhưng tầng application chỉ nhìn thấy `UserRepository`, còn `UserRepositoryImpl` đóng vai trò adapter kết nối hai thế giới này.

### 4.2. Bảo mật – JWT, Filter, Password Encoder, Security Config

#### 4.2.1. `JwtTokenProvider` – Sinh & xác thực JWT

**Vị trí**:  
`src/main/java/org/example/sep26management/infrastructure/security/JwtTokenProvider.java`

Chức năng:

- **Cấu hình**:
  - `jwt.secret` – key ký token (từ `application.properties` hoặc dùng default).
  - `jwt.expiration` – thời gian sống của token (ms), default 24h.
- **generateToken(username, role)**:
  - Tạo JWT với:
    - `subject = username`
    - claim `"role"` = role
    - `issuedAt`, `expiration`
  - Ký với HMAC (secret key).
- **getUsernameFromToken(token)**:
  - Parse token, lấy `subject`.
- **getRoleFromToken(token)**:
  - Parse token, lấy claim `"role"`.
- **validateToken(token)**:
  - Parse token, nếu không exception → hợp lệ, ngược lại → không hợp lệ.

Đây là nơi chứa **thuật toán xử lý token** (nhưng vẫn dựa trên thư viện jjwt).

#### 4.2.2. `JwtAuthenticationFilter` – Lấy token từ request & set SecurityContext

**Vị trí**:  
`src/main/java/org/example/sep26management/infrastructure/security/JwtAuthenticationFilter.java`

Luồng xử lý cho mỗi request:

1. Lấy header `Authorization`.
2. Nếu có dạng `"Bearer <token>"`:
   - Cắt phần `"Bearer "` để lấy token.
3. Gọi `jwtTokenProvider.validateToken(token)`:
   - Nếu token hợp lệ:
     - Lấy `username` và `role` từ token.
     - Tạo `UsernamePasswordAuthenticationToken` với:
       - principal = `username`
       - authorities = `ROLE_<role>`
     - Set vào `SecurityContextHolder`.
4. `filterChain.doFilter(request, response)` – tiếp tục chuỗi filter.

Kết quả:

- Sau filter này, các component trong Spring Security có thể biết **user hiện tại** và **role** của họ.

#### 4.2.3. `PasswordEncoderService` & `PasswordEncoderImpl` – Mã hóa mật khẩu

**`PasswordEncoderService`**:

- Interface định nghĩa:
  - `encode(String rawPassword)`
  - `matches(String rawPassword, String encodedPassword)`

**`PasswordEncoderImpl`**:

- Implement bằng `BCryptPasswordEncoder` (Spring Security).
- Dùng để:
  - Mã hóa mật khẩu khi **đăng ký**.
  - So sánh mật khẩu khi **đăng nhập**.

#### 4.2.4. `SecurityConfig` – Cấu hình Spring Security

**Vị trí**:  
`src/main/java/org/example/sep26management/infrastructure/config/SecurityConfig.java`

Chức năng:

- **Tắt CSRF** cho REST API: `.csrf(csrf -> csrf.disable())`.
- **CORS**: cho phép mọi origin, header, method (có thể siết chặt hơn trong production).
- **Session stateless**:
  - `session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)`
  - Vì dùng JWT nên không cần server-side session.
- **Phân quyền endpoint**:
  - `/api/auth/**` – `permitAll()`
  - `/api/health` – `permitAll()`
  - `/h2-console/**` và Swagger – `permitAll()`
  - Các request khác – `.anyRequest().authenticated()`
- **Thêm filter JWT**:
  - `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
  - Đảm bảo JWT filter chạy trước filter xác thực mặc định.

---

## 5. Tóm tắt luồng end-to-end cho từng use case

### 5.1. Luồng đăng nhập – `POST /api/auth/login`

1. **Client** gửi request:
   - `POST /api/auth/login`
   - Body JSON:
     - `username`
     - `password`
2. **AuthController**:
   - Map JSON → `LoginRequest`.
   - Validate `@NotBlank` trên `username`, `password`.
   - Gọi `authUseCase.login(request)`.
3. **AuthService.login**:
   - `userRepository.findByUsername(...)` → truy vấn User.
   - So sánh password với `PasswordEncoderService.matches`.
   - Kiểm tra `enabled`.
   - Sinh JWT bằng `JwtTokenProvider.generateToken`.
   - Map `User` → `UserDto` qua `UserMapper`.
   - Trả về `LoginResponse`.
4. **AuthController**:
   - Trả `200 OK` với `LoginResponse` (chứa `token` và `user`).
5. **DB**:
   - Truy vấn được thực hiện thông qua:
     - `UserRepository` → `UserRepositoryImpl` → `UserJpaRepository` → DB.

### 5.2. Luồng đăng ký – `POST /api/auth/register`

1. **Client** gửi request:
   - `POST /api/auth/register`
   - Body JSON: `username`, `email`, `password`, `firstName`, `lastName`.
2. **AuthController**:
   - Map JSON → `RegisterRequest`.
   - Validate (NotBlank, Size, Email).
   - Gọi `authUseCase.register(request)`.
3. **AuthService.register**:
   - Check trùng username/email qua `existsByUsername/existsByEmail`.
   - Mã hóa password với `PasswordEncoderService.encode`.
   - Tạo entity `User` mới.
   - Lưu vào DB qua `userRepository.save`.
   - Map `User` đã lưu → `UserDto`.
   - Trả `UserDto`.
4. **AuthController**:
   - Trả `201 Created` + body `UserDto`.
5. **DB**:
   - Insert bản ghi mới vào bảng `users`.

### 5.3. Luồng lấy user hiện tại – `GET /api/auth/me`

1. **Client** gửi request:
   - `GET /api/auth/me`
   - Header: `Authorization: Bearer <jwt_token>`
2. **AuthController**:
   - Nhận header `Authorization` (có thể gồm cả "Bearer ").
   - Gọi `authUseCase.getCurrentUser(tokenHeader)`.
3. **AuthService.getCurrentUser**:
   - Cắt tiền tố `"Bearer "` nếu có.
   - Validate token với `JwtTokenProvider.validateToken`.
   - Lấy `username` từ token.
   - `userRepository.findByUsername(username)` để lấy `User` từ DB.
   - Map sang `UserDto`.
4. **AuthController**:
   - Trả `200 OK` + `UserDto`.
5. **Bổ sung (nếu access endpoint khác)**:
   - Trước khi tới controller, `JwtAuthenticationFilter` đã đọc token, validate và set `SecurityContext`.

### 5.4. Luồng health check – `GET /api/health`

1. **Client** gửi request:
   - `GET /api/health`
2. **HealthController**:
   - Không gọi service, không truy vấn DB.
   - Tạo `Map<String, String>` với `status = "UP"`, `message = "Application is running"`.
   - Trả `200 OK` + JSON.

---

## 6. Tóm tắt nhanh theo câu hỏi của bạn

- **“Function đi từ đâu đến đâu?”**  
  - Ví dụ đăng nhập:
    - Client → `AuthController.login` → `AuthUseCase.login` → `AuthService.login` → `UserRepository` → `UserRepositoryImpl` → `UserJpaRepository` → DB.
    - Ngược lại từ DB → Entity `User` → `UserMapper` → `UserDto` → `LoginResponse` → JSON về client.

- **“Controller xử lý tác vụ nào?”**  
  - Controller chỉ:
    - Nhận & validate request.
    - Gọi use case (service).
    - Chuyển kết quả thành HTTP response.
  - Không chứa logic nghiệp vụ phức tạp.

- **“Hàm (use case) được triển khai ở folder nào, trong đó chứa những gì?”**  
  - Use case auth triển khai tại:
    - `application/service/AuthService.java`
  - Chứa:
    - Logic login: tìm user, check mật khẩu, check enabled, sinh JWT.
    - Logic register: check tồn tại username/email, mã hóa mật khẩu, lưu User.
    - Logic getCurrentUser: validate token, lấy username từ token, tìm user.

- **“Thuật toán được xử lý ở đâu?”**  
  - Chính yếu **nằm trong `AuthService`**:
    - Quy tắc validate nghiệp vụ (unique, enabled, token hợp lệ).
  - Phần xử lý token chi tiết nằm trong `JwtTokenProvider`.
  - Phần mã hóa mật khẩu nằm trong `PasswordEncoderImpl`.

- **“Cuối cùng là truy xuất database ở phần nào?”**  
  - Ở tầng infrastructure:
    - Domain gọi `UserRepository` (interface).
    - `UserRepositoryImpl` implement và ủy quyền cho:
    - `UserJpaRepository` (extends `JpaRepository<User, Long>`), đây là nơi Spring Data JPA sinh SQL và truy vấn DB thật sự.
  - Entity mapping cho bảng DB: `domain/entity/User.java` (kế thừa `BaseEntity`).

Bạn có thể mở file này cùng với các file controller/service/repository trong IDE để đối chiếu trực tiếp theo từng đoạn mã khi đọc.


