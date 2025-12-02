# Clean Architecture - SEP26Management

## Tổng quan

Dự án này được xây dựng theo mô hình **Clean Architecture**, đảm bảo sự tách biệt rõ ràng giữa các layer và dependency direction đúng đắn (dependencies hướng vào trong).

## Cấu trúc Layers

```
src/main/java/org/example/sep26management/
├── domain/              # Domain Layer (Core - không phụ thuộc bất kỳ layer nào)
│   ├── entity/          # Entities (Domain models)
│   ├── repository/      # Repository interfaces (Ports)
│   └── exception/       # Domain exceptions
│
├── application/         # Application Layer
│   ├── dto/             # Data Transfer Objects
│   ├── mapper/          # Entity <-> DTO mappers
│   ├── port/            # Use case interfaces (Ports)
│   └── service/         # Application services (Use case implementations)
│
├── infrastructure/      # Infrastructure Layer
│   ├── persistence/     # Repository implementations
│   │   └── jpa/         # JPA repositories
│   ├── config/          # Configuration classes
│   └── exception/       # Exception handlers
│
└── presentation/        # Presentation Layer
    └── controller/      # REST controllers
```

## Dependency Rule

**Quy tắc quan trọng:** Dependencies chỉ được hướng vào trong (inward)

```
Presentation → Application → Domain ← Infrastructure
```

- **Domain Layer**: Không phụ thuộc vào bất kỳ layer nào (core business logic)
- **Application Layer**: Chỉ phụ thuộc vào Domain Layer
- **Infrastructure Layer**: Phụ thuộc vào Domain và Application Layer
- **Presentation Layer**: Phụ thuộc vào Application Layer

## Chi tiết từng Layer

### 1. Domain Layer (`domain/`)

**Mục đích:** Chứa business logic cốt lõi, không phụ thuộc vào framework hay database.

**Components:**
- **Entities**: Domain models với business rules
- **Repository Interfaces**: Định nghĩa contracts cho data access (không có implementation)
- **Exceptions**: Domain-specific exceptions

**Ví dụ:**
- `BaseEntity`: Base class cho tất cả entities với common fields (id, createdAt, updatedAt)
- `ExampleEntity`: Domain entity mẫu
- `ExampleRepository`: Interface định nghĩa methods cho data access

### 2. Application Layer (`application/`)

**Mục đích:** Chứa use cases và business logic ứng dụng.

**Components:**
- **DTOs**: Data Transfer Objects để truyền dữ liệu giữa các layer
- **Ports (Interfaces)**: Định nghĩa use cases (ví dụ: `ExampleUseCase`)
- **Services**: Implementation của use cases
- **Mappers**: Chuyển đổi giữa Entity và DTO

**Ví dụ:**
- `ExampleUseCase`: Interface định nghĩa các use cases
- `ExampleService`: Implementation các use cases
- `ExampleMapper`: Chuyển đổi `ExampleEntity` ↔ `ExampleDto`

### 3. Infrastructure Layer (`infrastructure/`)

**Mục đích:** Implementation các technical concerns (database, external services, etc.)

**Components:**
- **Persistence**: Repository implementations (JPA, JDBC, etc.)
- **Config**: Configuration classes (JPA config, etc.)
- **Exception Handlers**: Global exception handling

**Ví dụ:**
- `ExampleRepositoryImpl`: Implementation của `ExampleRepository` interface
- `ExampleJpaRepository`: Spring Data JPA repository
- `GlobalExceptionHandler`: Xử lý exceptions toàn cục

### 4. Presentation Layer (`presentation/`)

**Mục đích:** Xử lý HTTP requests/responses, REST APIs.

**Components:**
- **Controllers**: REST controllers để xử lý HTTP requests

**Ví dụ:**
- `ExampleController`: REST endpoints cho Example resource
- `HealthController`: Health check endpoint

## API Endpoints

### Examples API

- `POST /api/examples` - Tạo example mới
- `GET /api/examples/{id}` - Lấy example theo ID
- `GET /api/examples` - Lấy tất cả examples
- `PUT /api/examples/{id}` - Cập nhật example
- `DELETE /api/examples/{id}` - Xóa example

### Health Check

- `GET /api/health` - Kiểm tra trạng thái ứng dụng

## Database

Project sử dụng **H2 Database** (in-memory) với Spring Data JPA.

**H2 Console:** http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

## Technology Stack

- **Java 17**
- **Spring Boot 4.0.0**
- **Spring Data JPA**
- **H2 Database**
- **Lombok**
- **Spring Validation**

## Cách chạy

```bash
mvn spring-boot:run
```

Hoặc:

```bash
./mvnw spring-boot:run
```

## Best Practices

1. **Domain Layer** không được import bất kỳ class nào từ các layer khác
2. **Repository Interfaces** chỉ được định nghĩa trong Domain Layer
3. **Use Case Interfaces** định nghĩa trong Application Layer
4. **Implementation** của repositories và services ở Infrastructure/Application Layer
5. Sử dụng **DTOs** để truyền dữ liệu giữa các layer, không expose entities trực tiếp
6. **Mappers** được sử dụng để chuyển đổi giữa Entity và DTO

## Mở rộng Project

Khi thêm tính năng mới:

1. **Domain Layer**: Tạo Entity và Repository Interface
2. **Application Layer**: Tạo DTOs, Use Case Interface, Service Implementation, Mapper
3. **Infrastructure Layer**: Implement Repository Interface
4. **Presentation Layer**: Tạo Controller với REST endpoints

