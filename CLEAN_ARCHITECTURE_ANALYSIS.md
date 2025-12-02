# Phân tích Chi Tiết Clean Architecture - SEP26Management

## 📋 Tổng Quan Cấu Trúc

Dự án được tổ chức theo **Clean Architecture** với 4 layers chính, tuân thủ nguyên tắc **dependency rule**: dependencies chỉ hướng vào trong (inward).

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  (Controllers - HTTP/REST API entry points)                 │
└──────────────────────┬──────────────────────────────────────┘
                       │ depends on
┌──────────────────────▼──────────────────────────────────────┐
│                   APPLICATION LAYER                          │
│  (Use Cases, Services, DTOs, Business Logic)                │
└──────────────────────┬──────────────────────────────────────┘
                       │ depends on
┌──────────────────────▼──────────────────────────────────────┐
│                     DOMAIN LAYER                             │
│  (Entities, Repository Interfaces, Exceptions)              │
│  ⚠️ KHÔNG PHỤ THUỘC BẤT KỲ LAYER NÀO                        │
└──────────────────────┬──────────────────────────────────────┘
                       │ implemented by
┌──────────────────────▼──────────────────────────────────────┐
│                 INFRASTRUCTURE LAYER                         │
│  (Repository Implementations, Config, External Services)    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🗂️ Chi Tiết Từng Layer và Folder

### 1️⃣ **DOMAIN LAYER** (`domain/`)
**Vai trò:** Chứa business logic cốt lõi, không phụ thuộc vào framework, database, hay bất kỳ layer nào khác.

#### 📁 `domain/entity/`
**Chức năng:** Định nghĩa các domain entities (business objects)

**Files:**
- **`BaseEntity.java`** 
  - Base class cho tất cả entities
  - Cung cấp common fields: `id`, `createdAt`, `updatedAt`
  - Tự động set timestamps khi persist/update (`@PrePersist`, `@PreUpdate`)
  - Sử dụng `@MappedSuperclass` để JPA không tạo table cho class này

- **`ExampleEntity.java`**
  - Domain entity cụ thể mô tả business object "Example"
  - Chứa business fields: `name`, `description`, `status`
  - Extends `BaseEntity` để kế thừa common fields
  - Sử dụng Lombok annotations để giảm boilerplate code
  - `@Entity` và `@Table`: ánh xạ với database table

**Nguyên tắc:**
- ✅ Chỉ chứa business logic và data
- ✅ Không import từ `application`, `infrastructure`, `presentation`
- ✅ Có thể sử dụng JPA annotations (đây là trade-off phổ biến)

---

#### 📁 `domain/repository/`
**Chức năng:** Định nghĩa contracts (interfaces) cho data access, KHÔNG có implementation

**Files:**
- **`ExampleRepository.java`**
  - Interface định nghĩa các operations cần thiết cho data access
  - Methods: `save()`, `findById()`, `findAll()`, `deleteById()`, `existsById()`
  - Chỉ làm việc với domain entities (`ExampleEntity`)
  - **KHÔNG** có implementation ở đây → implementation ở Infrastructure Layer

**Nguyên tắc:**
- ✅ Chỉ là interface (Port trong Hexagonal Architecture)
- ✅ Định nghĩa "WHAT" cần làm, không phải "HOW"
- ✅ Không phụ thuộc vào JPA, Spring Data, hay database cụ thể

**Ví dụ sử dụng:**
```java
// Application Layer sử dụng interface này
private final ExampleRepository exampleRepository; // ✅ Đúng

// Infrastructure Layer implement interface này
public class ExampleRepositoryImpl implements ExampleRepository { // ✅ Đúng
```

---

#### 📁 `domain/exception/`
**Chức năng:** Định nghĩa domain-specific exceptions

**Files:**
- **`DomainException.java`**
  - Base exception cho tất cả domain exceptions
  - Kế thừa `RuntimeException`

- **`EntityNotFoundException.java`**
  - Exception cụ thể khi entity không tìm thấy
  - Dùng trong business logic khi validate

**Nguyên tắc:**
- ✅ Domain exceptions chỉ được throw từ Domain/Application layer
- ✅ Infrastructure layer xử lý và convert thành HTTP responses

---

### 2️⃣ **APPLICATION LAYER** (`application/`)
**Vai trò:** Chứa use cases (business logic ứng dụng), orchestration logic, và data transformation.

#### 📁 `application/port/`
**Chức năng:** Định nghĩa use case interfaces (Ports) - "WHAT" business cần làm

**Files:**
- **`ExampleUseCase.java`**
  - Interface định nghĩa các use cases cho Example domain
  - Methods: `createExample()`, `getExampleById()`, `getAllExamples()`, `updateExample()`, `deleteExample()`
  - Làm việc với DTOs, không phải Entities trực tiếp
  - **Port** trong Hexagonal Architecture - định nghĩa contract

**Nguyên tắc:**
- ✅ Interface định nghĩa business operations
- ✅ Presentation layer sử dụng interface này, không phụ thuộc vào implementation
- ✅ Dễ dàng test bằng mock objects

---

#### 📁 `application/service/`
**Chức năng:** Implementation các use cases (business logic thực tế)

**Files:**
- **`ExampleService.java`**
  - Implement `ExampleUseCase` interface
  - Chứa business logic: validation, orchestration, transaction management
  - Sử dụng `ExampleRepository` (interface từ Domain) để persist data
  - Sử dụng `ExampleMapper` để convert Entity ↔ DTO
  - `@Transactional`: quản lý transactions
  - Throw domain exceptions khi có lỗi business logic

**Flow:**
1. Nhận DTO từ Presentation layer
2. Validate business rules
3. Convert DTO → Entity (dùng Mapper)
4. Gọi Repository để persist
5. Convert Entity → DTO (dùng Mapper)
6. Return DTO cho Presentation layer

**Nguyên tắc:**
- ✅ Chỉ phụ thuộc vào Domain layer (entities, repository interfaces)
- ✅ Không phụ thuộc vào Infrastructure (implementation details)
- ✅ Orchestration logic, không phải technical implementation

---

#### 📁 `application/dto/`
**Chức năng:** Data Transfer Objects - đối tượng truyền dữ liệu giữa các layers

**Files:**
- **`ExampleDto.java`**
  - DTO cho Example entity
  - Chứa tất cả fields cần expose ra ngoài
  - Không có business logic, chỉ là data container

- **`CreateExampleRequest.java`**
  - Request DTO khi tạo Example mới
  - Chỉ chứa fields cần thiết cho creation

- **`UpdateExampleRequest.java`**
  - Request DTO khi update Example
  - Chỉ chứa fields có thể update

**Nguyên tắc:**
- ✅ Tách biệt Entity (Domain) và DTO (Application/Presentation)
- ✅ Entities không được expose trực tiếp ra ngoài
- ✅ DTOs có thể thay đổi mà không ảnh hưởng Domain

**Lý do:**
- Bảo vệ Domain layer khỏi thay đổi API
- Tránh expose internal structure
- Dễ versioning API

---

#### 📁 `application/mapper/`
**Chức năng:** Convert giữa Entity và DTO

**Files:**
- **`ExampleMapper.java`**
  - Chuyển đổi `ExampleEntity` ↔ `ExampleDto`
  - Methods: `toDto()`, `toEntity()`
  - Tập trung logic mapping ở một nơi

**Nguyên tắc:**
- ✅ Single Responsibility: chỉ làm mapping
- ✅ Tránh mapping logic rải rác trong Service
- ✅ Dễ maintain và test

---

### 3️⃣ **INFRASTRUCTURE LAYER** (`infrastructure/`)
**Vai trò:** Implementation các technical concerns: database, external services, framework-specific code.

#### 📁 `infrastructure/persistence/`
**Chức năng:** Implementation của Repository interfaces từ Domain layer

**Files:**
- **`ExampleRepositoryImpl.java`**
  - Implement `ExampleRepository` interface (từ Domain layer)
  - **Adapter** trong Hexagonal Architecture
  - Delegate calls đến Spring Data JPA repository
  - Chuyển đổi giữa domain contract và technical implementation

**Flow:**
```
Application Layer → ExampleRepository (interface)
                              ↓
                   ExampleRepositoryImpl (implement)
                              ↓
                   ExampleJpaRepository (Spring Data JPA)
```

**Nguyên tắc:**
- ✅ Implement interface từ Domain layer
- ✅ Phụ thuộc vào Domain (interface)
- ✅ Sử dụng Spring Data JPA cho implementation details

---

#### 📁 `infrastructure/persistence/jpa/`
**Chức năng:** Spring Data JPA repositories - framework-specific code

**Files:**
- **`ExampleJpaRepository.java`**
  - Extends `JpaRepository<ExampleEntity, Long>`
  - Spring Data JPA tự động implement các CRUD operations
  - Có thể thêm custom query methods ở đây
  - Framework-specific, có thể thay đổi (ví dụ: từ JPA sang JDBC)

**Nguyên tắc:**
- ✅ Isolated - chỉ Infrastructure layer biết về JPA
- ✅ Domain/Application layer không biết JPA tồn tại
- ✅ Dễ thay đổi implementation (JPA → MongoDB, JDBC, etc.)

---

#### 📁 `infrastructure/exception/`
**Chức năng:** Xử lý exceptions và convert thành HTTP responses

**Files:**
- **`GlobalExceptionHandler.java`**
  - `@RestControllerAdvice`: catch exceptions toàn cục
  - Convert domain exceptions → HTTP responses
  - Xử lý `EntityNotFoundException` → 404
  - Xử lý `DomainException` → 400
  - Xử lý `Exception` → 500

**Flow:**
```
Domain Layer throws EntityNotFoundException
                ↓
Application Layer propagates exception
                ↓
Presentation Layer receives exception
                ↓
GlobalExceptionHandler catches & converts to HTTP response
```

**Nguyên tắc:**
- ✅ Technical concern (HTTP responses)
- ✅ Presentation layer không cần biết exception handling
- ✅ Centralized exception handling

---

#### 📁 `infrastructure/config/`
**Chức năng:** Configuration classes (hiện tại đã được move lên main class)

**Note:** Cấu hình JPA hiện tại được đặt trực tiếp trên `@SpringBootApplication` để đơn giản hóa.

---

### 4️⃣ **PRESENTATION LAYER** (`presentation/`)
**Vai trò:** Xử lý HTTP requests/responses, REST API endpoints.

#### 📁 `presentation/controller/`
**Chức năng:** REST controllers - entry points cho HTTP requests

**Files:**
- **`ExampleController.java`**
  - `@RestController`: Spring MVC annotation
  - `@RequestMapping("/api/examples")`: base path
  - Exposes REST endpoints:
    - `POST /api/examples` - Create
    - `GET /api/examples/{id}` - Read one
    - `GET /api/examples` - Read all
    - `PUT /api/examples/{id}` - Update
    - `DELETE /api/examples/{id}` - Delete
  - Sử dụng `ExampleUseCase` interface (không phụ thuộc vào implementation)
  - `@Valid`: validate request DTOs
  - Returns `ResponseEntity` với appropriate HTTP status codes

- **`HealthController.java`**
  - Health check endpoint
  - `GET /api/health` - Kiểm tra ứng dụng có running không

**Nguyên tắc:**
- ✅ Chỉ phụ thuộc vào Application layer (use case interfaces)
- ✅ Không chứa business logic
- ✅ Chỉ làm HTTP ↔ DTO conversion
- ✅ Thin layer - delegate mọi thứ cho Application layer

**Flow một request:**
```
HTTP Request → Controller
                    ↓
          Convert HTTP → DTO
                    ↓
          Call UseCase interface
                    ↓
          Convert DTO → HTTP Response
```

---

## 🔄 Dependency Flow (Luồng Phụ Thuộc)

```
Presentation Layer
    ↓ depends on interface
Application Layer  
    ↓ depends on interface
Domain Layer
    ↑ implemented by
Infrastructure Layer
```

### ✅ Dependency Rule được tuân thủ:

1. **Domain Layer** (center):
   - ❌ KHÔNG import từ Application, Infrastructure, Presentation
   - ✅ Chỉ chứa business logic thuần túy

2. **Application Layer**:
   - ✅ Chỉ import từ Domain layer
   - ❌ KHÔNG import từ Infrastructure hay Presentation

3. **Infrastructure Layer**:
   - ✅ Implement interfaces từ Domain layer
   - ✅ Có thể import từ Application layer (nếu cần)
   - ✅ Không được import bởi Domain hay Application

4. **Presentation Layer**:
   - ✅ Chỉ import từ Application layer (use case interfaces)
   - ❌ KHÔNG import trực tiếp từ Domain hay Infrastructure

---

## 🎯 Lợi Ích Của Clean Architecture

### 1. **Testability (Dễ Test)**
- Domain layer: Unit test thuần túy, không cần Spring
- Application layer: Mock repository interfaces
- Infrastructure layer: Integration tests

### 2. **Independence (Độc Lập)**
- Business logic không phụ thuộc framework
- Có thể đổi database (JPA → MongoDB) mà không ảnh hưởng Domain
- Có thể đổi framework (Spring → Quarkus) mà không ảnh hưởng business logic

### 3. **Maintainability (Dễ Bảo Trì)**
- Mỗi layer có trách nhiệm rõ ràng
- Thay đổi một layer không ảnh hưởng layers khác
- Code organization rõ ràng

### 4. **Scalability (Dễ Mở Rộng)**
- Dễ thêm use cases mới
- Dễ thêm API endpoints mới
- Dễ thêm data sources mới

---

## 📝 Ví Dụ Flow Hoàn Chỉnh

### Tạo Example mới:

```
1. HTTP POST /api/examples
   ↓
2. ExampleController.createExample()
   - Nhận CreateExampleRequest (DTO)
   ↓
3. ExampleService.createExample()
   - Validate business rules
   - Convert DTO → Entity (Mapper)
   - Call ExampleRepository.save()
   ↓
4. ExampleRepositoryImpl.save()
   - Implement ExampleRepository interface
   - Call ExampleJpaRepository.save()
   ↓
5. ExampleJpaRepository.save()
   - Spring Data JPA persist to database
   ↓
6. Entity → DTO (Mapper)
   ↓
7. Return DTO → Controller
   ↓
8. HTTP 201 Created với ExampleDto
```

### Lấy Example không tồn tại:

```
1. HTTP GET /api/examples/999
   ↓
2. ExampleController.getExampleById(999)
   ↓
3. ExampleService.getExampleById(999)
   - Call ExampleRepository.findById(999)
   ↓
4. Repository returns Optional.empty()
   ↓
5. Service throws EntityNotFoundException
   ↓
6. GlobalExceptionHandler catches exception
   ↓
7. Convert to HTTP 404 Not Found response
```

---

## 🔧 Cách Mở Rộng

### Thêm Entity mới:

1. **Domain Layer:**
   - Tạo entity trong `domain/entity/`
   - Tạo repository interface trong `domain/repository/`

2. **Application Layer:**
   - Tạo DTOs trong `application/dto/`
   - Tạo UseCase interface trong `application/port/`
   - Implement UseCase trong `application/service/`
   - Tạo Mapper trong `application/mapper/`

3. **Infrastructure Layer:**
   - Implement Repository trong `infrastructure/persistence/`
   - Tạo JPA Repository trong `infrastructure/persistence/jpa/`

4. **Presentation Layer:**
   - Tạo Controller trong `presentation/controller/`

---

## 📚 Tài Liệu Tham Khảo

- Clean Architecture - Robert C. Martin
- Hexagonal Architecture (Ports and Adapters)
- Spring Boot Best Practices
- Domain-Driven Design (DDD)

---

**Kết luận:** Dự án này tuân thủ đúng nguyên tắc Clean Architecture, đảm bảo business logic độc lập với framework và dễ dàng test, maintain, và mở rộng.

