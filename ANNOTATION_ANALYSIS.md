# Phân Tích Chi Tiết Các Annotation Trong Project SEP26Management

## Tổng Quan

Tài liệu này phân tích **CỰC KỲ CHI TIẾT** tất cả các annotation được sử dụng trong project SEP26Management, một ứng dụng Spring Boot theo kiến trúc Clean Architecture với JWT Authentication. 

Tài liệu bao gồm:
- **Cơ chế hoạt động** của từng annotation
- **Execution flow** và **lifecycle** 
- **Tương tác** giữa các annotation
- **Ví dụ thực tế** với request/response flow
- **Runtime behavior** và internal mechanism
- **Best practices** và **anti-patterns**
- **Troubleshooting** tips

---

## Mục Lục

1. [Spring Framework Annotations](#1-spring-framework-annotations)
2. [Jakarta Persistence API (JPA) Annotations](#2-jakarta-persistence-api-jpa-annotations)
3. [Jakarta Validation Annotations](#3-jakarta-validation-annotations)
4. [Lombok Annotations](#4-lombok-annotations)
5. [Swagger/OpenAPI Annotations](#5-swaggeropenapi-annotations)
6. [Request Flow Chi Tiết](#6-request-flow-chi-tiết)
7. [Lifecycle và Execution Order](#7-lifecycle-và-execution-order)
8. [Tương Tác Giữa Các Annotation](#8-tương-tác-giữa-các-annotation)

---

## 1. Spring Framework Annotations

### 1.1. Application Configuration

#### `@SpringBootApplication`
- **Vị trí**: `Sep26ManagementApplication.java`
- **Mục đích**: Annotation chính để khởi động Spring Boot application
- **Chức năng**:
  - Kết hợp `@Configuration`, `@EnableAutoConfiguration`, và `@ComponentScan`
  - Tự động cấu hình Spring Boot
  - Quét các component trong package được chỉ định
- **Tham số**:
  - `scanBasePackages = "org.example.sep26management"`: Chỉ định package để quét component

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Khi Application Khởi Động:**
   ```
   SpringApplication.run() được gọi
   ↓
   Spring Boot quét class có @SpringBootApplication
   ↓
   Phát hiện @ComponentScan với scanBasePackages
   ↓
   Quét tất cả package con của "org.example.sep26management"
   ↓
   Tìm các class có: @Component, @Service, @Repository, @Controller, @RestController
   ↓
   Tạo Spring beans và đăng ký vào ApplicationContext
   ```

2. **@Configuration (ẩn trong @SpringBootApplication):**
   - Cho phép class chứa các `@Bean` methods
   - Spring tạo CGLIB proxy để đảm bảo `@Bean` methods chỉ được gọi một lần

3. **@EnableAutoConfiguration (ẩn):**
   - Tự động cấu hình dựa trên classpath
   - Phát hiện: Spring Web → cấu hình DispatcherServlet
   - Phát hiện: JPA → cấu hình EntityManagerFactory, DataSource
   - Phát hiện: Security → cấu hình SecurityFilterChain

4. **@ComponentScan (ẩn):**
   - Quét package hiện tại và các package con
   - Tìm và đăng ký các Spring components
   - Trong project này: quét từ `org.example.sep26management` trở xuống

**Ví Dụ Thực Tế:**
```java
@SpringBootApplication(scanBasePackages = "org.example.sep26management")
public class Sep26ManagementApplication {
    public static void main(String[] args) {
        // Khi chạy dòng này:
        SpringApplication.run(Sep26ManagementApplication.class, args);
        
        // Spring sẽ:
        // 1. Quét package org.example.sep26management
        // 2. Tìm thấy AuthController (@RestController)
        // 3. Tìm thấy AuthService (@Service)
        // 4. Tìm thấy UserRepositoryImpl (@Repository)
        // 5. Tạo beans và inject dependencies
        // 6. Khởi động embedded Tomcat server
        // 7. Deploy application
    }
}
```

**Lưu Ý Quan Trọng:**
- Nếu không chỉ định `scanBasePackages`, Spring chỉ quét package của class chứa `@SpringBootApplication`
- Có thể chỉ định nhiều packages: `scanBasePackages = {"package1", "package2"}`
- `@SpringBootApplication` là meta-annotation, có thể thay thế bằng 3 annotations riêng lẻ

#### `@EnableJpaRepositories`
- **Vị trí**: `Sep26ManagementApplication.java`
- **Mục đích**: Kích hoạt JPA repositories
- **Tham số**:
  - `basePackages = "org.example.sep26management.infrastructure.persistence.jpa"`: Chỉ định package chứa JPA repositories

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Khi Application Khởi Động:**
   ```
   @EnableJpaRepositories được phát hiện
   ↓
   Spring Data JPA tạo RepositoryFactoryBean
   ↓
   Quét package chỉ định để tìm interfaces extends JpaRepository
   ↓
   Tìm thấy UserJpaRepository extends JpaRepository<User, Long>
   ↓
   Tạo proxy implementation cho UserJpaRepository
   ↓
   Implement các method như: save(), findById(), findAll()
   ↓
   Implement các method query từ tên method: findByUsername(), existsByEmail()
   ↓
   Đăng ký bean vào ApplicationContext
   ```

2. **Spring Data JPA Magic:**
   - Interface `UserJpaRepository` không có implementation
   - Spring Data JPA tự động tạo implementation tại runtime
   - Phân tích tên method để tạo query:
     ```java
     // Method name: findByUsername
     // Spring tạo: SELECT * FROM users WHERE username = ?
     
     // Method name: existsByEmail  
     // Spring tạo: SELECT COUNT(*) > 0 FROM users WHERE email = ?
     ```

3. **Tại Sao Cần basePackages?**
   - Trong Clean Architecture, JPA repositories nằm ở infrastructure layer
   - Không muốn quét toàn bộ project (tránh conflict)
   - Chỉ quét package cụ thể chứa JPA repositories

**Ví Dụ Thực Tế:**
```java
// Interface không có implementation
@Repository
public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    // Spring tự động tạo implementation:
    // SELECT u FROM User u WHERE u.username = :username
}

// Khi sử dụng:
@Autowired
private UserJpaRepository repository; // Spring inject proxy implementation

User user = repository.findByUsername("john");
// Spring thực thi query tự động tạo từ tên method
```

**Lưu Ý:**
- Nếu không chỉ định `basePackages`, Spring quét package của class chứa `@EnableJpaRepositories`
- Có thể chỉ định `entityManagerFactoryRef` và `transactionManagerRef` nếu có nhiều EntityManager

---

### 1.2. Web Layer (Presentation)

#### `@RestController`
- **Vị trí**: `AuthController.java`, `HealthController.java`
- **Mục đích**: Đánh dấu class là REST controller, kết hợp `@Controller` và `@ResponseBody`
- **Chức năng**: Tự động serialize response thành JSON/XML

**Cơ Chế Hoạt Động Chi Tiết:**

1. **@RestController = @Controller + @ResponseBody:**
   ```java
   // Tương đương với:
   @Controller
   @ResponseBody
   public class AuthController
   ```

2. **Request Flow Chi Tiết:**
   ```
   HTTP Request đến: POST /api/auth/login
   ↓
   DispatcherServlet nhận request
   ↓
   HandlerMapping tìm controller method phù hợp
   ↓
   Tìm thấy: AuthController.login()
   ↓
   @RequestBody deserialize JSON → LoginRequest object
   ↓
   @Valid validate LoginRequest (nếu fail → 400 Bad Request)
   ↓
   Gọi method: authUseCase.login(request)
   ↓
   Method trả về: LoginResponse
   ↓
   @ResponseBody (tự động) serialize LoginResponse → JSON
   ↓
   HTTP Response: 200 OK với JSON body
   ```

3. **Message Converters:**
   - Spring sử dụng `HttpMessageConverter` để convert
   - `MappingJackson2HttpMessageConverter` convert Java object ↔ JSON
   - Content-Type header quyết định converter nào được dùng
   - `Accept: application/json` → JSON response
   - `Content-Type: application/json` → JSON request

4. **So Sánh với @Controller:**
   ```java
   // @Controller (không có @ResponseBody)
   @Controller
   public class OldController {
       @GetMapping("/user")
       public String getUser() {
           return "user"; // Trả về view name (user.jsp, user.html)
       }
   }
   
   // @RestController (có @ResponseBody)
   @RestController
   public class AuthController {
       @GetMapping("/user")
       public UserDto getUser() {
           return userDto; // Trả về object → serialize thành JSON
       }
   }
   ```

**Ví Dụ Thực Tế:**
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Request body JSON:
        // {
        //   "username": "john",
        //   "password": "password123"
        // }
        // 
        // Spring tự động:
        // 1. Parse JSON
        // 2. Tạo LoginRequest object
        // 3. Set username = "john", password = "password123"
        // 4. Validate (nếu có @Valid)
        
        LoginResponse response = authUseCase.login(request);
        
        // Response object:
        // LoginResponse { token: "...", type: "Bearer", user: {...} }
        //
        // Spring tự động:
        // 1. Serialize thành JSON
        // 2. Set Content-Type: application/json
        // 3. Trả về HTTP 200 OK
        
        return ResponseEntity.ok(response);
    }
}
```

**Lưu Ý:**
- `@RestController` chỉ hoạt động khi có `spring-boot-starter-web`
- Có thể override `@ResponseBody` bằng cách return `ResponseEntity` với custom headers
- Có thể sử dụng `@Controller` + `@ResponseBody` trên method nếu muốn mix view và REST API

#### `@RequestMapping`
- **Vị trí**: `AuthController.java`, `HealthController.java`
- **Mục đích**: Map request URL đến controller hoặc method
- **Tham số**:
  - `value = "/api/auth"`: Base path cho tất cả endpoints trong controller

**Cơ Chế Hoạt Động Chi Tiết:**

1. **URL Mapping Mechanism:**
   ```
   @RequestMapping("/api/auth") trên class
   + @PostMapping("/login") trên method
   = Final URL: /api/auth/login
   ```

2. **HandlerMapping Process:**
   ```
   Request: POST /api/auth/login
   ↓
   DispatcherServlet nhận request
   ↓
   Gọi tất cả HandlerMapping beans
   ↓
   RequestMappingHandlerMapping kiểm tra:
   - Method: POST ✓
   - Path: /api/auth/login ✓
   ↓
   Tìm thấy: AuthController.login()
   ↓
   Trả về HandlerMethod
   ↓
   DispatcherServlet gọi HandlerAdapter
   ↓
   HandlerAdapter invoke method
   ```

3. **Các Tham Số Có Thể Dùng:**
   ```java
   @RequestMapping(
       value = "/api/auth",           // URL path
       method = RequestMethod.POST,   // HTTP method (GET, POST, PUT, DELETE)
       consumes = "application/json", // Content-Type của request
       produces = "application/json", // Content-Type của response
       params = "username",           // Yêu cầu parameter "username"
       headers = "Authorization"      // Yêu cầu header "Authorization"
   )
   ```

4. **Path Variables và Query Parameters:**
   ```java
   // Path variable
   @GetMapping("/users/{id}")
   public UserDto getUser(@PathVariable Long id) {
       // URL: /api/users/123 → id = 123
   }
   
   // Query parameter
   @GetMapping("/users")
   public List<UserDto> getUsers(@RequestParam String role) {
       // URL: /api/users?role=ADMIN → role = "ADMIN"
   }
   ```

**Ví Dụ Thực Tế Trong Project:**
```java
@RestController
@RequestMapping("/api/auth")  // Base path
public class AuthController {
    
    // Final URL: POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(...) { }
    
    // Final URL: POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(...) { }
    
    // Final URL: GET /api/auth/me
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(...) { }
}
```

**Lưu Ý:**
- `@RequestMapping` là parent của `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`
- Có thể đặt `@RequestMapping` trên method thay vì class
- Path matching hỗ trợ Ant-style patterns: `/api/**`, `/api/*/users`

#### `@PostMapping`
- **Vị trí**: `AuthController.java`
- **Mục đích**: Map HTTP POST request đến method
- **Tham số**:
  - `value = "/login"`, `"/register"`: Path cụ thể cho endpoint

```java
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(...)
```

#### `@GetMapping`
- **Vị trí**: `AuthController.java`, `HealthController.java`
- **Mục đích**: Map HTTP GET request đến method

```java
@GetMapping("/me")
public ResponseEntity<UserDto> getCurrentUser(...)
```

#### `@RequestBody`
- **Vị trí**: `AuthController.java`
- **Mục đích**: Bind HTTP request body (JSON) vào Java object
- **Thường kết hợp với**: `@Valid` để validate dữ liệu

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Request Body Processing Flow:**
   ```
   HTTP Request:
   POST /api/auth/login
   Content-Type: application/json
   Body: {"username": "john", "password": "123456"}
   ↓
   DispatcherServlet nhận request
   ↓
   HandlerAdapter xử lý
   ↓
   Tìm thấy parameter có @RequestBody
   ↓
   Xác định Content-Type: application/json
   ↓
   Chọn MappingJackson2HttpMessageConverter
   ↓
   Deserialize JSON string → LoginRequest object
   ↓
   Tạo LoginRequest instance
   ↓
   Set username = "john", password = "123456"
   ↓
   Pass object vào method parameter
   ```

2. **Message Converter Selection:**
   ```java
   // Spring kiểm tra Content-Type header
   Content-Type: application/json
   → Sử dụng MappingJackson2HttpMessageConverter
   
   Content-Type: application/xml
   → Sử dụng Jaxb2RootElementHttpMessageConverter
   
   Content-Type: application/x-www-form-urlencoded
   → Sử dụng FormHttpMessageConverter
   ```

3. **JSON Deserialization Process:**
   ```java
   // JSON từ client:
   {
     "username": "john",
     "password": "123456"
   }
   
   // Jackson ObjectMapper:
   1. Parse JSON string
   2. Tạo LoginRequest instance (cần @NoArgsConstructor)
   3. Map "username" → setUsername("john")
   4. Map "password" → setPassword("123456")
   5. Return LoginRequest object
   ```

4. **Kết Hợp với @Valid:**
   ```java
   @PostMapping("/login")
   public ResponseEntity<LoginResponse> login(
       @Valid @RequestBody LoginRequest request
   ) {
       // Execution order:
       // 1. @RequestBody: Deserialize JSON → LoginRequest
       // 2. @Valid: Validate LoginRequest
       //    - Check @NotBlank trên username
       //    - Check @NotBlank trên password
       // 3. Nếu validation fail:
       //    - Throw MethodArgumentNotValidException
       //    - @RestControllerAdvice bắt exception
       //    - Trả về 400 Bad Request
       // 4. Nếu validation pass:
       //    - Continue với method body
   }
   ```

5. **Error Handling:**
   ```java
   // Nếu JSON không hợp lệ:
   {
     "username": "john"
     // Thiếu dấu phẩy → JSON parse error
   }
   → HttpMessageNotReadableException
   → 400 Bad Request
   
   // Nếu thiếu @RequestBody:
   @PostMapping("/login")
   public ResponseEntity<LoginResponse> login(LoginRequest request) {
       // request sẽ là null hoặc giá trị mặc định
       // Không có dữ liệu từ request body
   }
   ```

**Ví Dụ Thực Tế:**
```java
// Client gửi request:
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Content-Length: 45

{"username":"john","password":"123456"}

// Spring xử lý:
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(
    @Valid @RequestBody LoginRequest request
) {
    // request.getUsername() = "john"
    // request.getPassword() = "123456"
    // Validation đã pass (có @Valid)
    
    LoginResponse response = authUseCase.login(request);
    return ResponseEntity.ok(response);
}
```

**Lưu Ý:**
- `@RequestBody` chỉ đọc request body một lần (stream chỉ đọc được một lần)
- Cần `@NoArgsConstructor` trong DTO để Jackson có thể tạo instance
- Có thể dùng `@JsonIgnore` để bỏ qua field khi deserialize
- Có thể dùng `@JsonProperty` để map field name khác với JSON key

#### `@RequestHeader`
- **Vị trí**: `AuthController.java`
- **Mục đích**: Bind HTTP header value vào method parameter

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Header Extraction Process:**
   ```
   HTTP Request:
   GET /api/auth/me
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ↓
   DispatcherServlet extract headers
   ↓
   Tìm thấy parameter có @RequestHeader("Authorization")
   ↓
   Lấy giá trị header "Authorization"
   ↓
   Pass vào method parameter: token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   ```

2. **Các Cách Sử Dụng:**
   ```java
   // Lấy header cụ thể
   @RequestHeader("Authorization") String token
   
   // Lấy tất cả headers
   @RequestHeader Map<String, String> headers
   
   // Lấy header với giá trị mặc định
   @RequestHeader(value = "Authorization", required = false) String token
   
   // Lấy header với tên khác
   @RequestHeader(value = "X-Custom-Header", defaultValue = "default") String custom
   ```

3. **Trong Project:**
   ```java
   @GetMapping("/me")
   public ResponseEntity<UserDto> getCurrentUser(
       @RequestHeader("Authorization") String token
   ) {
       // token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
       // Cần extract token (bỏ "Bearer " prefix)
       
       if (token != null && token.startsWith("Bearer ")) {
           token = token.substring(7); // Lấy phần sau "Bearer "
       }
       
       // Validate và sử dụng token
   }
   ```

4. **So Sánh với Các Cách Khác:**
   ```java
   // Cách 1: @RequestHeader (trong project)
   @GetMapping("/me")
   public ResponseEntity<UserDto> getCurrentUser(
       @RequestHeader("Authorization") String token
   ) { }
   
   // Cách 2: HttpServletRequest
   @GetMapping("/me")
   public ResponseEntity<UserDto> getCurrentUser(HttpServletRequest request) {
       String token = request.getHeader("Authorization");
   }
   
   // Cách 3: @RequestHeader với Map (lấy tất cả)
   @GetMapping("/me")
   public ResponseEntity<UserDto> getCurrentUser(
       @RequestHeader Map<String, String> headers
   ) {
       String token = headers.get("Authorization");
   }
   ```

**Ví Dụ Thực Tế:**
```java
// Client gửi request:
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqb2huIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE2OTk5OTk5OTl9.signature

// Spring xử lý:
@GetMapping("/me")
public ResponseEntity<UserDto> getCurrentUser(
    @RequestHeader("Authorization") String token
) {
    // token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    
    // Extract JWT token
    if (token != null && token.startsWith("Bearer ")) {
        token = token.substring(7);
    }
    
    // Validate token
    if (!jwtTokenProvider.validateToken(token)) {
        throw new InvalidCredentialsException("Invalid token");
    }
    
    // Get user info
    String username = jwtTokenProvider.getUsernameFromToken(token);
    UserDto user = authUseCase.getCurrentUser(token);
    
    return ResponseEntity.ok(user);
}
```

**Lưu Ý:**
- Header names không phân biệt hoa thường (case-insensitive)
- Nếu `required = true` (mặc định) và header không có → 400 Bad Request
- Có thể dùng `defaultValue` nếu header không bắt buộc
- Trong project này, có thể cải thiện bằng cách tạo custom `@RequestHeader` resolver để tự động extract JWT token

---

### 1.3. Service Layer (Application)

#### `@Service`
- **Vị trí**: `AuthService.java`
- **Mục đích**: Đánh dấu class là service component, được quản lý bởi Spring container
- **Chức năng**: Chứa business logic

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Component Scanning:**
   ```
   @SpringBootApplication scan package
   ↓
   Tìm thấy class có @Service
   ↓
   Tạo bean definition
   ↓
   Resolve dependencies (@Autowired, constructor injection)
   ↓
   Tạo bean instance
   ↓
   Apply AOP proxies (@Transactional, etc.)
   ↓
   Register vào ApplicationContext
   ↓
   Bean sẵn sàng để inject
   ```

2. **@Service vs @Component:**
   ```java
   // @Service = @Component với semantic meaning
   @Service  // Rõ ràng: đây là service layer
   public class AuthService { }
   
   // Tương đương với:
   @Component  // Generic component
   public class AuthService { }
   
   // Nhưng @Service tốt hơn vì:
   // - Rõ ràng về mục đích (business logic)
   // - Có thể filter trong code analysis tools
   // - Better documentation
   ```

3. **Dependency Injection:**
   ```java
   @Service
   @RequiredArgsConstructor  // Lombok tạo constructor với final fields
   @Transactional
   public class AuthService implements AuthUseCase {
       
       // Constructor injection (khuyến nghị)
       private final UserRepository userRepository;
       private final UserMapper userMapper;
       private final JwtTokenProvider jwtTokenProvider;
       private final PasswordEncoderService passwordEncoder;
       
       // Spring sẽ:
       // 1. Tìm tất cả beans phù hợp với type
       // 2. Inject vào constructor
       // 3. Tạo AuthService instance
   }
   ```

4. **Bean Lifecycle:**
   ```java
   @Service
   public class AuthService {
       
       // 1. Constructor được gọi
       public AuthService(UserRepository repo) {
           // Dependencies đã được inject
       }
       
       // 2. @PostConstruct (nếu có)
       @PostConstruct
       public void init() {
           // Initialization logic
       }
       
       // 3. Bean sẵn sàng sử dụng
       
       // 4. @PreDestroy (khi application shutdown)
       @PreDestroy
       public void cleanup() {
           // Cleanup logic
       }
   }
   ```

5. **Singleton Scope (Mặc định):**
   ```java
   // Mỗi ApplicationContext chỉ có 1 instance
   @Service
   public class AuthService { }
   
   // Khi inject:
   @Autowired
   private AuthService authService1; // Instance A
   
   @Autowired
   private AuthService authService2; // Cùng Instance A
   
   // authService1 == authService2 (true)
   ```

**Ví Dụ Thực Tế:**
```java
// 1. Service được định nghĩa
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements AuthUseCase {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
}

// 2. Controller inject service
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthUseCase authUseCase; // Spring inject AuthService
}

// 3. Khi request đến:
POST /api/auth/login
↓
AuthController.login() được gọi
↓
authUseCase.login() → AuthService.login()
↓
Business logic được thực thi
```

**Lưu Ý:**
- `@Service` là stereotype annotation, tương đương `@Component`
- Nên đặt ở application layer (business logic)
- Không nên đặt ở domain layer (pure business rules)
- Có thể đặt tên bean: `@Service("authService")`

#### `@Transactional`
- **Vị trí**: `AuthService.java`
- **Mục đích**: Quản lý transaction cho method hoặc class
- **Chức năng**:
  - Tự động bắt đầu transaction khi method được gọi
  - Commit nếu thành công, rollback nếu có exception
- **Tham số**:
  - `readOnly = true`: Tối ưu cho các method chỉ đọc dữ liệu

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Transaction Lifecycle:**
   ```
   Method được gọi
   ↓
   Spring AOP Proxy intercept
   ↓
   TransactionManager.beginTransaction()
   ↓
   Tạo Transaction object
   ↓
   Set isolation level, timeout, read-only flag
   ↓
   Bind transaction vào ThreadLocal
   ↓
   Execute method body
   ↓
   Nếu thành công:
     TransactionManager.commit()
     ↓
     Flush changes to database
     ↓
     Commit transaction
   ↓
   Nếu có exception (unchecked):
     TransactionManager.rollback()
     ↓
     Rollback tất cả changes
     ↓
     Throw exception
   ```

2. **AOP Proxy Mechanism:**
   ```java
   // Khi có @Transactional, Spring tạo proxy:
   public class AuthServiceProxy extends AuthService {
       private TransactionManager transactionManager;
       
       @Override
       public UserDto register(RegisterRequest request) {
           Transaction tx = transactionManager.beginTransaction();
           try {
               // Gọi method thực sự
               UserDto result = super.register(request);
               transactionManager.commit(tx);
               return result;
           } catch (Exception e) {
               transactionManager.rollback(tx);
               throw e;
           }
       }
   }
   ```

3. **readOnly = true:**
   ```java
   @Transactional(readOnly = true)
   public UserDto getCurrentUser(String token) {
       // Spring tối ưu:
       // - Set connection read-only
       // - Hibernate flush mode = MANUAL (không flush)
       // - Database có thể optimize query
       // - Không cho phép write operations
       
       User user = userRepository.findByUsername(username);
       // Nếu có: userRepository.save(user); → Exception!
   }
   ```

4. **Transaction Propagation:**
   ```java
   // Mặc định: Propagation.REQUIRED
   @Transactional
   public void methodA() {
       // Transaction 1 bắt đầu
       methodB(); // Sử dụng Transaction 1 (join)
   }
   
   @Transactional
   public void methodB() {
       // Sử dụng transaction hiện tại (không tạo mới)
   }
   
   // Propagation.REQUIRES_NEW
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public void methodC() {
       // Tạo transaction mới (không join transaction hiện tại)
   }
   ```

5. **Rollback Rules:**
   ```java
   @Transactional
   public void register(RegisterRequest request) {
       // Mặc định: rollback khi có RuntimeException hoặc Error
       // Không rollback khi có checked Exception
       
       userRepository.save(user);
       // Nếu có NullPointerException → rollback ✓
       // Nếu có SQLException → rollback ✓
       // Nếu có IOException → không rollback (checked exception)
   }
   
   // Custom rollback:
   @Transactional(rollbackFor = Exception.class)
   public void register(RegisterRequest request) {
       // Rollback cho tất cả exceptions
   }
   ```

**Ví Dụ Thực Tế Trong Project:**
```java
@Service
@Transactional  // Áp dụng cho tất cả methods
public class AuthService {
    
    @Transactional  // Override class-level
    public UserDto register(RegisterRequest request) {
        // Transaction bắt đầu
        
        // Check username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DomainException("Username already exists");
            // Exception → rollback transaction
        }
        
        // Encode password
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        // Create user
        User user = User.builder()
            .username(request.getUsername())
            .password(encodedPassword)
            .build();
        
        // Save to database
        User saved = userRepository.save(user);
        // Chưa commit, chỉ flush vào database
        
        // Transaction commit (nếu không có exception)
        // → INSERT INTO users ... được thực thi
        
        return userMapper.toDto(saved);
    }
    
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String token) {
        // Read-only transaction
        // Tối ưu cho SELECT queries
        
        String username = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        
        // SELECT query được thực thi
        // Không có write operations
        
        return userMapper.toDto(user);
        // Transaction commit (read-only, không có changes)
    }
}
```

**Lưu Ý Quan Trọng:**
- `@Transactional` chỉ hoạt động với Spring-managed beans (phải được Spring inject)
- Self-invocation không hoạt động (gọi method trong cùng class không qua proxy)
- Transaction boundary: method level > class level
- `readOnly = true` không đảm bảo database read-only, chỉ là hint cho optimization

---

### 1.4. Repository Layer (Infrastructure)

#### `@Repository`
- **Vị trí**: `UserRepositoryImpl.java`, `UserJpaRepository.java`
- **Mục đích**: Đánh dấu class là repository, xử lý data access
- **Chức năng**: Tự động chuyển đổi các exception thành DataAccessException

```java
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository
```

---

### 1.5. Component Layer

#### `@Component`
- **Vị trí**: `UserMapper.java`, `JwtAuthenticationFilter.java`, `JwtTokenProvider.java`, `PasswordEncoderImpl.java`
- **Mục đích**: Đánh dấu class là Spring component (generic stereotype)
- **Chức năng**: Được Spring quét và quản lý như một bean

```java
@Component
public class UserMapper
```

---

### 1.6. Configuration Layer

#### `@Configuration`
- **Vị trí**: `SecurityConfig.java`, `OpenApiConfig.java`
- **Mục đích**: Đánh dấu class chứa bean definitions
- **Chức năng**: Thay thế XML configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig
```

#### `@EnableWebSecurity`
- **Vị trí**: `SecurityConfig.java`
- **Mục đích**: Kích hoạt Spring Security configuration
- **Chức năng**: Cho phép custom security configuration

```java
@EnableWebSecurity
public class SecurityConfig
```

#### `@Bean`
- **Vị trí**: `SecurityConfig.java`, `OpenApiConfig.java`
- **Mục đích**: Đánh dấu method trả về một Spring bean
- **Chức năng**: Method được gọi bởi Spring container và kết quả được quản lý như bean

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http)

@Bean
public OpenAPI customOpenAPI()
```

#### `@Autowired`
- **Vị trí**: `SecurityConfig.java`
- **Mục đích**: Tự động inject dependency
- **Lưu ý**: Có thể thay thế bằng constructor injection (khuyến nghị)

```java
@Autowired
private JwtAuthenticationFilter jwtAuthenticationFilter;
```

#### `@Value`
- **Vị trí**: `OpenApiConfig.java`, `JwtTokenProvider.java`
- **Mục đích**: Inject giá trị từ properties file hoặc environment variable
- **Tham số**:
  - `"${server.port:8080}"`: Lấy giá trị từ properties, mặc định là 8080
  - `"${jwt.secret:...}"`: Lấy secret key, có giá trị mặc định

```java
@Value("${server.port:8080}")
private String serverPort;

@Value("${jwt.secret:MySecretKeyForJWTTokenGenerationMustBeAtLeast256BitsLongForHS512Algorithm}")
private String jwtSecret;
```

---

### 1.7. Exception Handling

#### `@RestControllerAdvice`
- **Vị trí**: `GlobalExceptionHandler.java`
- **Mục đích**: Đánh dấu class xử lý exception toàn cục cho REST controllers
- **Chức năng**: Kết hợp `@ControllerAdvice` và `@ResponseBody`

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
```

#### `@ExceptionHandler`
- **Vị trí**: `GlobalExceptionHandler.java`
- **Mục đích**: Xử lý exception cụ thể
- **Tham số**: Class của exception cần xử lý

```java
@ExceptionHandler(EntityNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleEntityNotFoundException(...)

@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleGenericException(...)
```

---

## 2. Jakarta Persistence API (JPA) Annotations

### 2.1. Entity Mapping

#### `@Entity`
- **Vị trí**: `User.java`
- **Mục đích**: Đánh dấu class là JPA entity, map với database table
- **Chức năng**: JPA sẽ quản lý lifecycle của entity

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Entity Registration:**
   ```
   Application startup
   ↓
   Hibernate scan entities
   ↓
   Tìm class có @Entity
   ↓
   Analyze entity metadata:
   - Table name (@Table)
   - Columns (@Column)
   - Relationships (@OneToMany, @ManyToOne)
   - Primary key (@Id)
   ↓
   Create EntityManager metadata
   ↓
   Register vào PersistenceContext
   ```

2. **Entity Lifecycle States:**
   ```java
   // 1. NEW (Transient)
   User user = new User();
   user.setUsername("john");
   // Chưa được quản lý bởi JPA
   
   // 2. MANAGED (Persistent)
   userRepository.save(user);
   // Entity được quản lý bởi EntityManager
   // Changes được track
   
   // 3. DETACHED
   entityManager.detach(user);
   // Không còn được quản lý
   // Changes không được track
   
   // 4. REMOVED
   userRepository.delete(user);
   // Đánh dấu để xóa
   // Sẽ bị xóa khi commit transaction
   ```

3. **Persistence Context:**
   ```java
   @Service
   @Transactional
   public class AuthService {
       public UserDto register(RegisterRequest request) {
           User user = User.builder()
               .username("john")
               .build();
           
           // Entity state: NEW (Transient)
           
           User saved = userRepository.save(user);
           // Entity state: MANAGED (Persistent)
           // EntityManager track changes
           
           saved.setEmail("john@example.com");
           // Change được track
           // Không cần gọi save() lại
           
           // Khi transaction commit:
           // - @PreUpdate được gọi (nếu có)
           // - UPDATE query được generate
           // - Execute UPDATE statement
       }
   }
   ```

**Ví Dụ Thực Tế:**
```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    // JPA sẽ:
    // 1. Map class User → table "users"
    // 2. Map fields → columns
    // 3. Manage entity lifecycle
    // 4. Track changes
    // 5. Generate SQL queries
}
```

**Lưu Ý:**
- Class phải có `@NoArgsConstructor` (JPA requirement)
- Class không được final (Hibernate cần proxy)
- Fields nên là private với getters/setters

#### `@Table`
- **Vị trí**: `User.java`
- **Mục đích**: Chỉ định tên table và các constraint
- **Tham số**:
  - `name = "users"`: Tên table trong database
  - `uniqueConstraints`: Định nghĩa unique constraints

```java
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "username"),
    @UniqueConstraint(columnNames = "email")
})
```

#### `@UniqueConstraint`
- **Vị trí**: `User.java` (trong `@Table`)
- **Mục đích**: Định nghĩa unique constraint cho column(s)

```java
@UniqueConstraint(columnNames = "username")
```

#### `@Column`
- **Vị trí**: `User.java`, `BaseEntity.java`
- **Mục đích**: Map field với database column
- **Tham số**:
  - `name`: Tên column trong database
  - `nullable`: Cho phép null hay không
  - `unique`: Column có unique constraint không
  - `updatable`: Cho phép update hay không

```java
@Column(name = "username", nullable = false, unique = true)
private String username;

@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

#### `@Id`
- **Vị trí**: `BaseEntity.java`
- **Mục đích**: Đánh dấu field là primary key

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

#### `@GeneratedValue`
- **Vị trí**: `BaseEntity.java`
- **Mục đích**: Chỉ định strategy để generate primary key
- **Tham số**:
  - `strategy = GenerationType.IDENTITY`: Sử dụng auto-increment của database

```java
@GeneratedValue(strategy = GenerationType.IDENTITY)
```

#### `@MappedSuperclass`
- **Vị trí**: `BaseEntity.java`
- **Mục đích**: Đánh dấu class là superclass cho các entity khác
- **Chức năng**: Các field trong class này sẽ được map vào các entity con

```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity
```

---

### 2.2. Entity Lifecycle Callbacks

#### `@PrePersist`
- **Vị trí**: `BaseEntity.java`
- **Mục đích**: Method được gọi trước khi entity được persist (insert) lần đầu
- **Chức năng**: Thường dùng để set giá trị mặc định như `createdAt`

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Lifecycle Callback Execution:**
   ```
   userRepository.save(user)
   ↓
   EntityManager.persist(user)
   ↓
   Check entity state: NEW
   ↓
   @PrePersist method được gọi
   ↓
   onCreate() execute:
     createdAt = LocalDateTime.now()
     updatedAt = LocalDateTime.now()
   ↓
   Entity được add vào PersistenceContext
   ↓
   Generate INSERT SQL
   ↓
   Execute INSERT (khi transaction commit)
   ```

2. **Timing:**
   ```java
   @PrePersist
   protected void onCreate() {
       // Được gọi:
       // - TRƯỚC KHI entity được persist
       // - TRONG transaction context
       // - TRƯỚC KHI INSERT SQL được generate
       // - Một lần duy nhất (chỉ khi persist lần đầu)
       
       createdAt = LocalDateTime.now();
       updatedAt = LocalDateTime.now();
   }
   ```

3. **Use Cases:**
   ```java
   @PrePersist
   protected void onCreate() {
       // 1. Set timestamps
       createdAt = LocalDateTime.now();
       updatedAt = LocalDateTime.now();
       
       // 2. Set default values
       if (enabled == null) {
           enabled = true;
       }
       
       // 3. Generate IDs (nếu không dùng @GeneratedValue)
       if (id == null) {
           id = UUID.randomUUID();
       }
       
       // 4. Set audit fields
       createdBy = SecurityContextHolder.getContext()
           .getAuthentication().getName();
   }
   ```

**Ví Dụ Thực Tế:**
```java
@Service
@Transactional
public class AuthService {
    public UserDto register(RegisterRequest request) {
        User user = User.builder()
            .username("john")
            .email("john@example.com")
            .build();
        // createdAt = null, updatedAt = null
        
        User saved = userRepository.save(user);
        // JPA gọi @PrePersist
        // onCreate() được execute
        // createdAt = 2024-01-01T10:00:00
        // updatedAt = 2024-01-01T10:00:00
        
        // INSERT INTO users (username, email, created_at, updated_at)
        // VALUES ('john', 'john@example.com', '2024-01-01T10:00:00', '2024-01-01T10:00:00')
        
        return userMapper.toDto(saved);
    }
}
```

**Lưu Ý:**
- Method phải là `void` hoặc return `void`
- Method có thể là `protected`, `private`, hoặc `public`
- Không được có parameters
- Được gọi trong transaction context

#### `@PreUpdate`
- **Vị trí**: `BaseEntity.java`
- **Mục đích**: Method được gọi trước khi entity được update
- **Chức năng**: Thường dùng để update `updatedAt`

```java
@PreUpdate
protected void onUpdate() {
    updatedAt = LocalDateTime.now();
}
```

---

## 3. Jakarta Validation Annotations

### 3.1. Validation Annotations

#### `@Valid`
- **Vị trí**: `AuthController.java`
- **Mục đích**: Kích hoạt validation cho object
- **Chức năng**: Validate các constraint trong object trước khi method được thực thi

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Validation Flow:**
   ```
   @RequestBody deserialize JSON → LoginRequest
   ↓
   @Valid được phát hiện
   ↓
   MethodValidationInterceptor intercept
   ↓
   Validator.validate(loginRequest)
   ↓
   Check tất cả constraints:
   - @NotBlank trên username
   - @NotBlank trên password
   ↓
   Nếu tất cả pass:
     Continue với method execution
   ↓
   Nếu có constraint fail:
     Collect violations
     ↓
     Throw MethodArgumentNotValidException
     ↓
     @RestControllerAdvice handle
     ↓
     Return 400 Bad Request với error details
   ```

2. **Validation Process:**
   ```java
   @PostMapping("/login")
   public ResponseEntity<LoginResponse> login(
       @Valid @RequestBody LoginRequest request
   ) {
       // Validation steps:
       // 1. @RequestBody: JSON → LoginRequest object
       // 2. @Valid trigger validation
       // 3. Validator check constraints:
       //    - request.username: @NotBlank
       //      → Check: not null, not empty, not blank
       //    - request.password: @NotBlank
       //      → Check: not null, not empty, not blank
       // 4. If all pass → continue
       // 5. If any fail → exception
   }
   ```

3. **Error Response:**
   ```java
   // Nếu validation fail:
   {
     "timestamp": "2024-01-01T10:00:00",
     "status": 400,
     "error": "Bad Request",
     "message": "Validation failed",
     "errors": [
       {
         "field": "username",
         "message": "Username is required"
       },
       {
         "field": "password",
         "message": "Password is required"
       }
     ]
   }
   ```

4. **Nested Validation:**
   ```java
   public class RegisterRequest {
       @NotBlank
       private String username;
       
       @Valid  // Validate nested object
       private Address address;
   }
   
   public class Address {
       @NotBlank
       private String street;
   }
   ```

**Ví Dụ Thực Tế:**
```java
// Request:
POST /api/auth/login
{
  "username": "",  // Empty string
  "password": "123"
}

// Validation:
@NotBlank(message = "Username is required")
private String username;  // FAIL: empty string

// Result:
MethodArgumentNotValidException
→ 400 Bad Request
{
  "errors": [
    {
      "field": "username",
      "message": "Username is required"
    }
  ]
}
```

**Lưu Ý:**
- `@Valid` chỉ hoạt động với Jakarta Validation annotations
- Cần dependency `spring-boot-starter-validation`
- Validation chỉ chạy khi có `@Valid` annotation
- Có thể validate nested objects với `@Valid`

#### `@NotBlank`
- **Vị trí**: `LoginRequest.java`, `RegisterRequest.java`
- **Mục đích**: Validate field không được null, empty hoặc chỉ chứa whitespace
- **Tham số**:
  - `message`: Thông báo lỗi khi validation fail

```java
@NotBlank(message = "Username is required")
private String username;
```

#### `@Email`
- **Vị trí**: `RegisterRequest.java`
- **Mục đích**: Validate field phải là email hợp lệ
- **Tham số**:
  - `message`: Thông báo lỗi

```java
@Email(message = "Email should be valid")
private String email;
```

#### `@Size`
- **Vị trí**: `RegisterRequest.java`
- **Mục đích**: Validate độ dài của String, Collection, Map, Array
- **Tham số**:
  - `min`: Độ dài tối thiểu
  - `max`: Độ dài tối đa
  - `message`: Thông báo lỗi

```java
@Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
private String username;

@Size(min = 6, message = "Password must be at least 6 characters")
private String password;
```

---

## 4. Lombok Annotations

### 4.1. Data Annotations

#### `@Data`
- **Vị trí**: `LoginRequest.java`, `LoginResponse.java`, `RegisterRequest.java`, `UserDto.java`
- **Mục đích**: Tự động generate getter, setter, toString, equals, hashCode
- **Chức năng**: Kết hợp `@Getter`, `@Setter`, `@ToString`, `@EqualsAndHashCode`, `@RequiredArgsConstructor`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest
```

#### `@Getter`
- **Vị trí**: `User.java`, `BaseEntity.java`
- **Mục đích**: Tự động generate getter methods cho tất cả fields

```java
@Getter
@Setter
public class User
```

#### `@Setter`
- **Vị trí**: `User.java`, `BaseEntity.java`
- **Mục đích**: Tự động generate setter methods cho tất cả fields

```java
@Getter
@Setter
public class User
```

#### `@Builder`
- **Vị trí**: `User.java`, `LoginResponse.java`, `UserDto.java`
- **Mục đích**: Tự động generate builder pattern cho class
- **Chức năng**: Cho phép tạo object theo fluent API

```java
@Builder
public class User

User user = User.builder()
    .username("john")
    .email("john@example.com")
    .build();
```

#### `@Builder.Default`
- **Vị trí**: `User.java`
- **Mục đích**: Chỉ định giá trị mặc định cho field trong builder
- **Lưu ý**: Phải đặt sau `@Builder`

```java
@Column(name = "enabled", nullable = false)
@Builder.Default
private Boolean enabled = true;
```

#### `@NoArgsConstructor`
- **Vị trí**: Hầu hết các DTO và Entity
- **Mục đích**: Tự động generate constructor không tham số
- **Lưu ý**: Cần thiết cho JPA và Jackson

```java
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest
```

#### `@AllArgsConstructor`
- **Vị trí**: Hầu hết các DTO và Entity
- **Mục đích**: Tự động generate constructor với tất cả fields

```java
@AllArgsConstructor
public class LoginRequest
```

#### `@RequiredArgsConstructor`
- **Vị trí**: `AuthController.java`, `AuthService.java`, `UserRepositoryImpl.java`, `JwtAuthenticationFilter.java`
- **Mục đích**: Tự động generate constructor với các `final` fields
- **Chức năng**: Hỗ trợ constructor injection (khuyến nghị hơn `@Autowired`)

**Cơ Chế Hoạt Động Chi Tiết:**

1. **Code Generation:**
   ```java
   // Source code:
   @RequiredArgsConstructor
   public class AuthController {
       private final AuthUseCase authUseCase;
   }
   
   // Lombok generate (compile time):
   public class AuthController {
       private final AuthUseCase authUseCase;
       
       // Generated constructor:
       public AuthController(AuthUseCase authUseCase) {
           this.authUseCase = authUseCase;
       }
   }
   ```

2. **Dependency Injection Flow:**
   ```
   Spring scan @Component, @Service, @Repository, @RestController
   ↓
   Tìm AuthController có @RequiredArgsConstructor
   ↓
   Analyze constructor parameters:
   - AuthUseCase authUseCase
   ↓
   Tìm bean có type AuthUseCase
   ↓
   Tìm thấy: AuthService implements AuthUseCase
   ↓
   Inject AuthService vào constructor
   ↓
   Tạo AuthController instance
   ```

3. **So Sánh với @Autowired:**
   ```java
   // ❌ Field injection (không khuyến nghị)
   @RestController
   public class AuthController {
       @Autowired
       private AuthUseCase authUseCase;
       // Khó test (phải dùng reflection)
       // Không thể final
   }
   
   // ✅ Constructor injection (khuyến nghị)
   @RestController
   @RequiredArgsConstructor
   public class AuthController {
       private final AuthUseCase authUseCase;
       // Dễ test (có thể mock constructor)
       // Immutable (final)
       // Lombok generate constructor tự động
   }
   ```

4. **Multiple Dependencies:**
   ```java
   @Service
   @RequiredArgsConstructor
   public class AuthService {
       private final UserRepository userRepository;
       private final UserMapper userMapper;
       private final JwtTokenProvider jwtTokenProvider;
       private final PasswordEncoderService passwordEncoder;
       
       // Lombok generate:
       // public AuthService(
       //     UserRepository userRepository,
       //     UserMapper userMapper,
       //     JwtTokenProvider jwtTokenProvider,
       //     PasswordEncoderService passwordEncoder
       // ) {
       //     this.userRepository = userRepository;
       //     this.userMapper = userMapper;
       //     this.jwtTokenProvider = jwtTokenProvider;
       //     this.passwordEncoder = passwordEncoder;
       // }
   }
   ```

**Ví Dụ Thực Tế:**
```java
// 1. Controller với @RequiredArgsConstructor
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthUseCase authUseCase;
    // Spring inject AuthService (implements AuthUseCase)
}

// 2. Service với nhiều dependencies
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements AuthUseCase {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    // Spring inject tất cả dependencies
}

// 3. Repository implementation
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository jpaRepository;
    // Spring inject JPA repository
}
```

**Lưu Ý:**
- Chỉ generate constructor cho `final` fields và `@NonNull` fields
- Không generate constructor nếu đã có constructor khác (trừ khi có `@RequiredArgsConstructor(force = true)`)
- Tốt hơn `@Autowired` vì hỗ trợ immutability và testability

#### `@Slf4j`
- **Vị trí**: `GlobalExceptionHandler.java`
- **Mục đích**: Tự động generate logger field (SLF4J)
- **Chức năng**: Tạo field `log` để sử dụng logging

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    log.error("Entity not found: {}", ex.getMessage());
}
```

---

## 5. Swagger/OpenAPI Annotations

### 5.1. API Documentation

#### `@Tag`
- **Vị trí**: `AuthController.java`, `HealthController.java`
- **Mục đích**: Nhóm các endpoints trong Swagger UI
- **Tham số**:
  - `name`: Tên tag
  - `description`: Mô tả tag

```java
@Tag(name = "Authentication", description = "Authentication API endpoints for login, register and user management")
public class AuthController
```

#### `@Operation`
- **Vị trí**: `AuthController.java`, `HealthController.java`
- **Mục đích**: Mô tả chi tiết cho endpoint
- **Tham số**:
  - `summary`: Tóm tắt ngắn gọn
  - `description`: Mô tả chi tiết

```java
@Operation(
    summary = "User login",
    description = "Authenticate user with username and password. Returns JWT token on success."
)
@PostMapping("/login")
```

#### `@ApiResponses`
- **Vị trí**: `AuthController.java`, `HealthController.java`
- **Mục đích**: Định nghĩa danh sách các response có thể xảy ra
- **Tham số**: Mảng các `@ApiResponse`

```java
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Login successful", ...),
    @ApiResponse(responseCode = "401", description = "Invalid credentials", ...)
})
```

#### `@ApiResponse`
- **Vị trí**: `AuthController.java`, `HealthController.java` (trong `@ApiResponses`)
- **Mục đích**: Mô tả một response cụ thể
- **Tham số**:
  - `responseCode`: HTTP status code
  - `description`: Mô tả response
  - `content`: Schema của response body

```java
@ApiResponse(
    responseCode = "200",
    description = "Login successful",
    content = @Content(schema = @Schema(implementation = LoginResponse.class))
)
```

#### `@Content`
- **Vị trí**: `AuthController.java`, `HealthController.java` (trong `@ApiResponse`)
- **Mục đích**: Định nghĩa nội dung của response
- **Tham số**: `schema` để chỉ định kiểu dữ liệu

```java
content = @Content(schema = @Schema(implementation = LoginResponse.class))
```

#### `@Schema`
- **Vị trí**: `AuthController.java`, `HealthController.java` (trong `@Content`)
- **Mục đích**: Định nghĩa schema cho response/request body
- **Tham số**:
  - `implementation`: Class được sử dụng làm schema

```java
schema = @Schema(implementation = LoginResponse.class)
```

#### `@SecurityRequirement`
- **Vị trí**: `AuthController.java`
- **Mục đích**: Chỉ định endpoint yêu cầu authentication
- **Tham số**:
  - `name`: Tên của security scheme (được định nghĩa trong `OpenApiConfig`)

```java
@SecurityRequirement(name = "bearerAuth")
@GetMapping("/me")
```

---

## 6. Tổng Kết Theo Layer

### 6.1. Presentation Layer
- `@RestController`
- `@RequestMapping`
- `@PostMapping`, `@GetMapping`
- `@RequestBody`, `@RequestHeader`
- `@Valid`
- `@Tag`, `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Content`, `@Schema`, `@SecurityRequirement`

### 6.2. Application Layer
- `@Service`
- `@Transactional`
- `@Component` (cho Mapper)
- `@RequiredArgsConstructor`

### 6.3. Domain Layer
- `@Entity`
- `@Table`, `@UniqueConstraint`
- `@Column`
- `@Id`, `@GeneratedValue`
- `@MappedSuperclass`
- `@PrePersist`, `@PreUpdate`
- `@Getter`, `@Setter`
- `@Builder`, `@Builder.Default`
- `@NoArgsConstructor`, `@AllArgsConstructor`

### 6.4. Infrastructure Layer
- `@Repository`
- `@Component`
- `@Configuration`
- `@EnableWebSecurity`
- `@Bean`
- `@Autowired`, `@Value`
- `@RestControllerAdvice`
- `@ExceptionHandler`

### 6.5. DTO Layer
- `@Data`
- `@Builder`
- `@NoArgsConstructor`, `@AllArgsConstructor`
- `@NotBlank`, `@Email`, `@Size`

---

## 7. Best Practices Được Áp Dụng

1. **Dependency Injection**: Sử dụng `@RequiredArgsConstructor` thay vì `@Autowired` (constructor injection)
2. **Validation**: Sử dụng `@Valid` kết hợp với Jakarta Validation annotations
3. **Transaction Management**: Sử dụng `@Transactional` với `readOnly = true` cho các method chỉ đọc
4. **API Documentation**: Sử dụng Swagger/OpenAPI annotations để tự động generate API docs
5. **Entity Lifecycle**: Sử dụng `@PrePersist` và `@PreUpdate` để tự động set timestamps
6. **Code Generation**: Sử dụng Lombok để giảm boilerplate code
7. **Exception Handling**: Sử dụng `@RestControllerAdvice` để xử lý exception tập trung

---

## 8. Lưu Ý Quan Trọng

1. **Lombok và JPA**: Cần `@NoArgsConstructor` cho JPA entities
2. **Builder và JPA**: Cần `@AllArgsConstructor` khi sử dụng `@Builder` với JPA
3. **Validation**: Cần `@Valid` trong controller để kích hoạt validation
4. **Transaction**: `@Transactional` chỉ hoạt động với Spring-managed beans
5. **Security**: `@EnableWebSecurity` cần `@Configuration` để hoạt động
6. **Swagger**: Các annotation Swagger chỉ có tác dụng khi có dependency `springdoc-openapi`

---

## 9. Dependencies Liên Quan

Các annotation được hỗ trợ bởi các dependencies sau:

- **Spring Boot**: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`
- **Jakarta Validation**: `spring-boot-starter-validation`
- **Lombok**: `lombok`
- **Swagger/OpenAPI**: `springdoc-openapi-starter-webmvc-ui`
- **JPA**: `spring-boot-starter-data-jpa` (chứa Jakarta Persistence API)

---

## 6. Request Flow Chi Tiết

### 6.1. Complete Request Flow: Login Endpoint

Dưới đây là flow hoàn chỉnh khi client gọi endpoint `/api/auth/login`:

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CLIENT REQUEST                                               │
└─────────────────────────────────────────────────────────────────┘
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "john",
  "password": "password123"
}

┌─────────────────────────────────────────────────────────────────┐
│ 2. SPRING BOOT RECEIVES REQUEST                                 │
└─────────────────────────────────────────────────────────────────┘
Embedded Tomcat Server
  ↓
DispatcherServlet (Spring MVC Front Controller)
  ↓
HandlerMapping: Tìm controller method phù hợp
  - Method: POST ✓
  - Path: /api/auth/login ✓
  - Tìm thấy: AuthController.login()

┌─────────────────────────────────────────────────────────────────┐
│ 3. REQUEST PROCESSING                                           │
└─────────────────────────────────────────────────────────────────┘
HandlerAdapter.invokeHandlerMethod()
  ↓
@RequestBody Processing:
  - Content-Type: application/json
  - Chọn MappingJackson2HttpMessageConverter
  - Deserialize JSON → LoginRequest object
  - LoginRequest.username = "john"
  - LoginRequest.password = "password123"

┌─────────────────────────────────────────────────────────────────┐
│ 4. VALIDATION (@Valid)                                          │
└─────────────────────────────────────────────────────────────────┘
MethodValidationInterceptor
  ↓
Validate LoginRequest:
  - @NotBlank trên username: "john" ✓ (pass)
  - @NotBlank trên password: "password123" ✓ (pass)
  ↓
Validation passed → Continue
  ↓
Nếu validation fail:
  - Throw MethodArgumentNotValidException
  - @RestControllerAdvice bắt exception
  - Trả về 400 Bad Request với error details

┌─────────────────────────────────────────────────────────────────┐
│ 5. SECURITY FILTER CHAIN                                        │
└─────────────────────────────────────────────────────────────────┘
SecurityFilterChain (SecurityConfig)
  ↓
JwtAuthenticationFilter.doFilterInternal()
  - Check Authorization header
  - Validate JWT token (nếu có)
  - Set SecurityContext
  ↓
RequestMatcher: /api/auth/** → permitAll()
  - Không cần authentication cho login endpoint
  - Continue filter chain

┌─────────────────────────────────────────────────────────────────┐
│ 6. CONTROLLER METHOD EXECUTION                                  │
└─────────────────────────────────────────────────────────────────┘
AuthController.login(@Valid @RequestBody LoginRequest request)
  ↓
authUseCase.login(request)
  - authUseCase là AuthService (injected via @RequiredArgsConstructor)

┌─────────────────────────────────────────────────────────────────┐
│ 7. SERVICE LAYER (@Service, @Transactional)                     │
└─────────────────────────────────────────────────────────────────┘
AuthService.login(LoginRequest request)
  ↓
@Transactional Proxy intercept:
  - TransactionManager.beginTransaction()
  - Set transaction isolation level
  - Bind transaction to ThreadLocal
  ↓
Method body execution:
  1. userRepository.findByUsername("john")
     - @Repository: UserRepositoryImpl
     - JPA: SELECT * FROM users WHERE username = 'john'
     - Return Optional<User>
  
  2. Check user exists:
     - If empty → throw InvalidCredentialsException
  
  3. passwordEncoder.matches("password123", user.getPassword())
     - BCrypt compare
     - If not match → throw InvalidCredentialsException
  
  4. Check user.enabled:
     - If false → throw DomainException
  
  5. jwtTokenProvider.generateToken(username, role)
     - Create JWT token
     - Sign with secret key
  
  6. Build LoginResponse:
     - LoginResponse.builder()
       .token(jwtToken)
       .type("Bearer")
       .user(userMapper.toDto(user))
       .build()
  ↓
@Transactional commit:
  - TransactionManager.commit()
  - Flush changes (nếu có)
  - Release connection

┌─────────────────────────────────────────────────────────────────┐
│ 8. EXCEPTION HANDLING (@RestControllerAdvice)                   │
└─────────────────────────────────────────────────────────────────┘
Nếu có exception:
  ↓
@RestControllerAdvice intercept
  ↓
@ExceptionHandler(InvalidCredentialsException.class)
  - Log error
  - Create error response
  - Return 401 Unauthorized
  ↓
@ExceptionHandler(DomainException.class)
  - Log error
  - Create error response
  - Return 400 Bad Request

┌─────────────────────────────────────────────────────────────────┐
│ 9. RESPONSE SERIALIZATION (@ResponseBody)                       │
└─────────────────────────────────────────────────────────────────┘
LoginResponse object
  ↓
@ResponseBody (tự động với @RestController)
  ↓
MappingJackson2HttpMessageConverter
  - Serialize LoginResponse → JSON
  - Set Content-Type: application/json
  ↓
HTTP Response:
HTTP/1.1 200 OK
Content-Type: application/json

{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "user": {
    "id": 1,
    "username": "john",
    "email": "john@example.com",
    ...
  }
}
```

### 6.2. Request Flow: Get Current User (Authenticated)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CLIENT REQUEST (với JWT Token)                               │
└─────────────────────────────────────────────────────────────────┘
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

┌─────────────────────────────────────────────────────────────────┐
│ 2. SECURITY FILTER CHAIN                                        │
└─────────────────────────────────────────────────────────────────┘
JwtAuthenticationFilter.doFilterInternal()
  ↓
Extract token từ Authorization header:
  - "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  - Remove "Bearer " prefix
  - Token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  ↓
jwtTokenProvider.validateToken(token)
  - Verify signature
  - Check expiration
  - If valid → Continue
  ↓
jwtTokenProvider.getUsernameFromToken(token)
  - Extract username from JWT claims
  - username = "john"
  ↓
Create Authentication object:
  - UsernamePasswordAuthenticationToken
  - Principal: "john"
  - Authorities: [ROLE_USER]
  ↓
SecurityContextHolder.getContext().setAuthentication(auth)
  - Set authentication vào SecurityContext
  - Available trong request lifecycle

┌─────────────────────────────────────────────────────────────────┐
│ 3. CONTROLLER METHOD                                            │
└─────────────────────────────────────────────────────────────────┘
AuthController.getCurrentUser(@RequestHeader("Authorization") String token)
  ↓
@RequestHeader extract:
  - token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  ↓
authUseCase.getCurrentUser(token)

┌─────────────────────────────────────────────────────────────────┐
│ 4. SERVICE LAYER (@Transactional(readOnly = true))              │
└─────────────────────────────────────────────────────────────────┘
AuthService.getCurrentUser(String token)
  ↓
@Transactional(readOnly = true):
  - Begin read-only transaction
  - Optimize for SELECT queries
  ↓
Extract token (remove "Bearer " prefix)
  ↓
jwtTokenProvider.validateToken(token)
  ↓
jwtTokenProvider.getUsernameFromToken(token)
  - username = "john"
  ↓
userRepository.findByUsername("john")
  - SELECT * FROM users WHERE username = 'john'
  - Return Optional<User>
  ↓
userMapper.toDto(user)
  - Convert User entity → UserDto
  ↓
Return UserDto
  ↓
Transaction commit (read-only, no changes)

┌─────────────────────────────────────────────────────────────────┐
│ 5. RESPONSE                                                     │
└─────────────────────────────────────────────────────────────────┘
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "username": "john",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "enabled": true,
  "role": "USER",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

---

## 7. Lifecycle và Execution Order

### 7.1. Application Startup Lifecycle

```
1. JVM Start
   ↓
2. SpringApplication.run() được gọi
   ↓
3. @SpringBootApplication được phát hiện
   ↓
4. Component Scanning:
   - Quét package: org.example.sep26management
   - Tìm các class có: @Component, @Service, @Repository, @Controller, @RestController
   ↓
5. Bean Creation (theo thứ tự dependency):
   a. Configuration Beans (@Configuration):
      - SecurityConfig
      - OpenApiConfig
   
   b. Infrastructure Beans:
      - PasswordEncoderImpl (@Component)
      - JwtTokenProvider (@Component)
      - JwtAuthenticationFilter (@Component)
      - UserJpaRepository (Spring Data JPA proxy)
      - UserRepositoryImpl (@Repository)
   
   c. Application Beans:
      - UserMapper (@Component)
      - AuthService (@Service)
   
   d. Presentation Beans:
      - AuthController (@RestController)
      - HealthController (@RestController)
      - GlobalExceptionHandler (@RestControllerAdvice)
   ↓
6. Dependency Injection:
   - Inject dependencies vào constructors
   - Apply @Autowired (nếu có)
   - Apply @Value (inject properties)
   ↓
7. AOP Proxy Creation:
   - @Transactional → Transaction proxy
   - Security → Security proxy
   ↓
8. @PostConstruct Methods (nếu có)
   ↓
9. Application Context Ready
   ↓
10. Embedded Tomcat Start
    ↓
11. Deploy Application
    ↓
12. Application Ready
```

### 7.2. Bean Creation Order

```java
// 1. Configuration classes được tạo trước
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(...) {
        // Bean được tạo ngay
    }
}

// 2. Infrastructure beans
@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String secret; // Injected từ properties
}

// 3. Repository beans
@Repository
public class UserRepositoryImpl {
    private final UserJpaRepository jpaRepository; // Injected
}

// 4. Service beans
@Service
public class AuthService {
    private final UserRepository userRepository; // Injected
    private final JwtTokenProvider jwtTokenProvider; // Injected
}

// 5. Controller beans
@RestController
public class AuthController {
    private final AuthUseCase authUseCase; // Injected
}
```

### 7.3. Method Execution Order trong Request

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request
    ) {
        // Execution order:
        // 1. DispatcherServlet nhận request
        // 2. HandlerMapping tìm method
        // 3. HandlerAdapter prepare
        // 4. @RequestBody deserialize JSON
        // 5. @Valid validate (nếu fail → exception)
        // 6. Security filters (JwtAuthenticationFilter)
        // 7. Method body execute
        // 8. @ResponseBody serialize response
        // 9. Return HTTP response
    }
}
```

---

## 8. Tương Tác Giữa Các Annotation

### 8.1. @RestController + @RequestBody + @Valid

```java
@RestController  // Tự động có @ResponseBody
public class AuthController {
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid           // 1. Validate sau khi deserialize
        @RequestBody     // 2. Deserialize JSON → object
        LoginRequest request
    ) {
        // Execution:
        // 1. @RequestBody: JSON → LoginRequest
        // 2. @Valid: Validate LoginRequest
        //    - Check @NotBlank, @Email, @Size
        // 3. Nếu valid → continue
        // 4. Nếu invalid → MethodArgumentNotValidException
        //    → @RestControllerAdvice handle
    }
}
```

### 8.2. @Service + @Transactional + @Repository

```java
@Service
@Transactional  // Class-level: áp dụng cho tất cả methods
public class AuthService {
    
    @Transactional  // Method-level: override class-level
    public UserDto register(RegisterRequest request) {
        // Transaction bắt đầu
        
        userRepository.save(user);  // @Repository
        // - JPA EntityManager trong transaction
        // - Changes được track
        // - Chưa commit vào database
        
        // Nếu exception → rollback
        // Nếu thành công → commit
    }
    
    @Transactional(readOnly = true)  // Override với read-only
    public UserDto getCurrentUser(String token) {
        // Read-only transaction
        // Tối ưu cho SELECT queries
        
        return userRepository.findByUsername(username);
        // SELECT query được thực thi
        // Không có write operations
    }
}
```

### 8.3. @Entity + @PrePersist + @PreUpdate

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    // ...
}

@MappedSuperclass
public abstract class BaseEntity {
    
    @PrePersist  // JPA lifecycle callback
    protected void onCreate() {
        // Được gọi TRƯỚC KHI entity được persist lần đầu
        // Trong transaction context
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate  // JPA lifecycle callback
    protected void onUpdate() {
        // Được gọi TRƯỚC KHI entity được update
        // Trong transaction context
        updatedAt = LocalDateTime.now();
    }
}

// Khi sử dụng:
@Service
@Transactional
public class AuthService {
    public UserDto register(RegisterRequest request) {
        User user = User.builder()
            .username("john")
            .build();
        // createdAt và updatedAt = null
        
        userRepository.save(user);
        // JPA gọi @PrePersist
        // onCreate() được execute
        // createdAt = now(), updatedAt = now()
        // Sau đó INSERT INTO users ...
    }
}
```

### 8.4. @RestControllerAdvice + @ExceptionHandler

```java
@RestControllerAdvice  // Intercept tất cả exceptions từ @RestController
public class GlobalExceptionHandler {
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handle(...) {
        // Chỉ bắt InvalidCredentialsException
        // Từ bất kỳ @RestController nào
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(...) {
        // Bắt tất cả exceptions còn lại
        // Phải đặt cuối cùng (most specific first)
    }
}

// Flow:
@RestController
public class AuthController {
    public ResponseEntity<LoginResponse> login(...) {
        throw new InvalidCredentialsException();
        // Exception được throw
        // ↓
        // @RestControllerAdvice intercept
        // ↓
        // @ExceptionHandler(InvalidCredentialsException.class) handle
        // ↓
        // Return ResponseEntity với error details
    }
}
```

### 8.5. @Component + @RequiredArgsConstructor (Dependency Injection)

```java
@Component
@RequiredArgsConstructor  // Lombok generate constructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    // Final fields → constructor parameters
    private final JwtTokenProvider jwtTokenProvider;
    
    // Lombok generate:
    // public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    //     this.jwtTokenProvider = jwtTokenProvider;
    // }
}

// Spring sẽ:
// 1. Tìm JwtTokenProvider bean
// 2. Inject vào constructor
// 3. Tạo JwtAuthenticationFilter instance
```

---

## 9. Best Practices và Anti-Patterns

### 9.1. Best Practices

1. **Dependency Injection:**
   ```java
   // ✅ GOOD: Constructor injection
   @Service
   @RequiredArgsConstructor
   public class AuthService {
       private final UserRepository userRepository;
   }
   
   // ❌ BAD: Field injection
   @Service
   public class AuthService {
       @Autowired
       private UserRepository userRepository;
   }
   ```

2. **Transaction Management:**
   ```java
   // ✅ GOOD: Method-level với read-only
   @Transactional(readOnly = true)
   public UserDto getCurrentUser(String token) { }
   
   // ❌ BAD: Class-level cho tất cả methods
   @Transactional
   public class AuthService {
       public UserDto getCurrentUser(String token) {
           // Unnecessary transaction overhead
       }
   }
   ```

3. **Validation:**
   ```java
   // ✅ GOOD: Validate ở controller
   @PostMapping("/login")
   public ResponseEntity<LoginResponse> login(
       @Valid @RequestBody LoginRequest request
   ) { }
   
   // ❌ BAD: Validate ở service
   public LoginResponse login(LoginRequest request) {
       if (request.getUsername() == null) {
           throw new ValidationException();
       }
   }
   ```

### 9.2. Common Mistakes

1. **@Transactional không hoạt động:**
   ```java
   // ❌ WRONG: Self-invocation
   @Service
   public class AuthService {
       public void methodA() {
           methodB(); // Không qua proxy → @Transactional không hoạt động
       }
       
       @Transactional
       public void methodB() { }
   }
   
   // ✅ CORRECT: Inject service
   @Service
   public class AuthService {
       @Autowired
       private AuthService self; // Proxy
       
       public void methodA() {
           self.methodB(); // Qua proxy → @Transactional hoạt động
       }
   }
   ```

2. **@RequestBody không hoạt động:**
   ```java
   // ❌ WRONG: Thiếu @NoArgsConstructor
   public class LoginRequest {
       private String username;
       // Không có constructor → Jackson không thể tạo instance
   }
   
   // ✅ CORRECT: Có @NoArgsConstructor
   @Data
   @NoArgsConstructor
   public class LoginRequest {
       private String username;
   }
   ```

---

## 10. Troubleshooting Tips

### 10.1. Bean không được tìm thấy

**Lỗi:** `NoSuchBeanDefinitionException: No qualifying bean of type 'X'`

**Nguyên nhân:**
- Class không có `@Component`, `@Service`, `@Repository`
- Package không được scan bởi `@ComponentScan`
- Bean chưa được tạo (circular dependency)

**Giải pháp:**
```java
// Kiểm tra:
1. Class có annotation phù hợp?
2. Package có trong scanBasePackages?
3. Có circular dependency?
```

### 10.2. @Transactional không hoạt động

**Lỗi:** Changes không được commit hoặc rollback

**Nguyên nhân:**
- Method không phải public
- Self-invocation
- Exception bị catch và không rethrow

**Giải pháp:**
```java
// ✅ CORRECT
@Transactional
public void method() {
    // Public method
    // Không catch exception (hoặc rethrow)
}
```

### 10.3. Validation không hoạt động

**Lỗi:** `@NotBlank`, `@Email` không validate

**Nguyên nhân:**
- Thiếu `@Valid` trên parameter
- Thiếu dependency `spring-boot-starter-validation`

**Giải pháp:**
```java
// ✅ CORRECT
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(
    @Valid @RequestBody LoginRequest request
) { }
```

---

*Tài liệu được tạo tự động dựa trên phân tích source code của project SEP26Management*

