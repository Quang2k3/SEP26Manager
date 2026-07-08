# SEP26Manager - Backend API 🚀

> **Spring Boot 3.x Backend + PostgreSQL Database + Redis Cache + WebSocket STOMP Messaging**
> 
> Hệ thống quản lý kho hàng hóa mỹ phẩm/hóa chất (Warehouse Management System - WMS) tích hợp thiết bị quét barcode cầm tay, kiểm định chất lượng (QC), gợi ý cất hàng (Putaway Suggestion), xuất hóa đơn PDF tự động và thông báo thời gian thực.

---

## 🏗️ Tech Stack & Infrastructure

*   **Core Backend**: Java 17, Spring Boot 3.2.0, Maven
*   **Database**: PostgreSQL 15 (DB-First approach, `ddl-auto: none/validate`)
*   **Connection Pool**: HikariCP (Tối ưu kết nối đồng thời)
*   **Caching & Session**: Redis 7 (Lettuce client) lưu trữ Scanner OTP, Scanner Session với cơ chế TTL tự động hủy
*   **Real-time Communication**: 
    *   **Spring WebSocket (STOMP Broker)**: Đẩy thông báo tức thời tới Client qua phân quyền vai trò (`/topic/notifications/{ROLE}`) hoặc kênh cá nhân (`/user/queue/notifications`).
    *   **Server-Sent Events (SSE)**: Đẩy trực tiếp snapshot quét hàng thời gian thực từ thiết bị di động lên Dashboard quản lý.
*   **Security & Authentication**: Spring Security + JWT Bearer Tokens (Custom filter gán thông tin `userId` và `warehouseIds` vào Principal).
*   **Document & Data Processing**:
    *   **iText PDF (5.x)**: Sinh phiếu xuất kho chuyên nghiệp (Landscape A4) hỗ trợ hiển thị tiếng Việt Unicode.
    *   **Apache POI (5.2.x)**: Xử lý tệp tin Excel nhập danh mục hàng hóa (SKU) hàng loạt.
    *   **Cloudinary**: Lưu trữ tệp tin đính kèm, ảnh đại diện SKU và ảnh chụp phiếu ký xác nhận của tài xế.
    *   **Gmail SMTP**: Tự động gửi mật khẩu tạm thời cho tài khoản mới và gửi mã OTP kết nối thiết bị quét.
*   **CI/CD & DevOps**: GitHub Actions (Tự động chạy test, build Docker image, đẩy lên Docker Hub và deploy lên VPS), Docker & Docker Compose.
*   **Testing**: JUnit 5, Mockito, H2 Database (In-memory testing profile), JaCoCo (Thu thập và xuất báo cáo độ bao phủ mã nguồn - Code Coverage).
*   **API Documentation**: Springdoc OpenAPI (Swagger UI).

---

## 🛠️ Trách Nhiệm & Đóng Góp Kỹ Thuật (Developer Responsibilities)

Dưới đây là mô tả chi tiết các trách nhiệm và đóng góp kỹ thuật chính trong dự án này, được trình bày bằng cả tiếng Việt và tiếng Anh để hỗ trợ viết báo cáo dự án hoặc CV:

### 🇻🇳 Phiên bản tiếng Việt
*   **Phát triển luồng nghiệp vụ kho cốt lõi (Core WMS Engine)**: Thiết kế và triển khai toàn bộ quy trình kho Inbound & Outbound, bao gồm: Tạo phiếu nhận hàng (Receiving Order), tạo phiếu nhập kho (GRN - Goods Receipt Note), sinh nhiệm vụ cất hàng (Putaway Task), kiểm định chất lượng (QC Inspection), quản lý đơn xuất hàng (Sales Order), chuyển kho (Transfer Order), nhiệm vụ nhặt hàng (Picking Task), và quản lý tồn kho chi tiết theo Lô sản phẩm (Inventory Lot tracking) quản lý ngày sản xuất/hạn sử dụng để cảnh báo trùng LOT thời gian thực.
*   **Tích hợp thiết bị quét cầm tay & Cơ chế Xác thực OTP (Secure QR Scanner Flow)**: Xây dựng cơ chế liên kết thiết bị di động (iPhone/Tablet) của thủ kho thành máy quét mã vạch không dây. Thiết kế luồng xác thực bảo mật 2 bước: tạo QR code chứa `sessionId`, sinh OTP lưu trữ tạm thời trên Redis cache với TTL tự động hủy, gửi OTP qua Email (Gmail SMTP) và xác thực trên thiết bị để cấp JWT token tạm thời (`SCANNER_TEMP` authority) cho thiết bị quét.
*   **Xử lý sự kiện quét mã vạch thời gian thực & Phân loại lỗi chất lượng (Scan Events & QC Routing)**: Phát triển API nhận tọa độ quét mã vạch từ thiết bị cầm tay. Hỗ trợ phân loại trạng thái hàng hóa ngay khi quét (`PASS`/`FAIL`). Nếu quét lỗi (`FAIL`), yêu cầu bắt buộc lý do (`reasonCode` như `LEAK`, `TORN_PACKAGING`) để hệ thống tự động đẩy lô hàng vào luồng kiểm tra chất lượng (QC), cô lập hàng lỗi (Quarantine Hold), và đẩy thông báo cập nhật snapshot thời gian thực lên Dashboard của quản lý qua Server-Sent Events (SSE).
*   **Import dữ liệu SKU hàng loạt từ Excel**: Phát triển mô-đun nhập danh mục hàng hóa (SKU) số lượng lớn bằng file Excel `.xlsx` sử dụng Apache POI. Thiết lập bộ quy tắc kiểm tra (file tối đa 5MB, giới hạn dưới 1000 dòng). Thiết kế thuật toán đối chiếu trùng lặp mã SKU (duplicate check) đồng thời trên cơ sở dữ liệu và ngay trong tệp tải lên để bỏ qua các dòng trùng lặp, ghi nhận lỗi chi tiết theo từng dòng (row-by-row) giúp quá trình import không bị gián đoạn hoàn toàn.
*   **Thuật toán Gợi ý Vị trí Cất hàng tự động (Putaway Suggestion Engine)**: Thiết kế công cụ gợi ý vị trí lưu kho tối ưu dựa trên quy tắc khớp Zone-Category (`Z-category_code`). Thuật toán tự động quét các BIN trong zone phù hợp, tính toán dung tích trống dựa trên snapshot tồn kho thực tế (`maxCapacity - occupiedQty`), chấm điểm và gợi ý BIN tối ưu nhất, hỗ trợ chia nhỏ lô hàng (split load) nếu BIN không đủ sức chứa.
*   **Tự động hóa xuất hóa đơn PDF và Tách biệt giao dịch (Dispatch PDF Generator & REQUIRES_NEW)**: Sử dụng thư viện iText tạo phiếu xuất kho PDF (khổ A4 nằm ngang) có thiết kế chuyên nghiệp, tích hợp font chữ hỗ trợ tiếng Việt Unicode. Thiết lập cơ chế tách biệt giao dịch (`Propagation.REQUIRES_NEW`) khi upload PDF lên Cloudinary để đảm bảo nếu dịch vụ bên thứ ba gặp sự cố mạng, giao dịch xác nhận xuất kho cốt lõi trong DB vẫn thành công và không bị rollback.
*   **Xác nhận giao hàng bằng Chữ ký số di động (QR & Mobile Signature Confirmation)**: Triển khai luồng xác thực hoàn tất đơn hàng: sau khi in phiếu xuất kho và ký tay, tài xế/thủ kho quét mã QR trên phiếu để mở trang tải ảnh, chụp ảnh phiếu đã ký và upload lên Cloudinary để lưu trữ minh chứng giao hàng thực tế (`signedNoteUrl`), tự động khóa đơn hàng sang trạng thái `COMPLETED` để chống sửa đổi dữ liệu.
*   **Hệ thống Thông báo Đa kênh thời gian thực (STOMP WebSocket Broker)**: Thiết kế WebSocket Broker sử dụng giao thức STOMP để đẩy thông báo lập tức cho các tác vụ quan trọng (phê duyệt GRN, sự cố phát sinh, yêu cầu nhặt hàng). Thực hiện xác thực JWT Bearer trực tiếp trong handshake của STOMP, định tuyến thông báo theo vai trò nhóm (`/topic/notifications/{ROLE}`) hoặc gửi đích danh cho từng cá nhân (`/user/queue/notifications`).
*   **Ghi nhận Lịch sử Hệ thống (Regulatory Audit Logging)**: Cấu hình cơ chế ghi nhận nhật ký hệ thống toàn diện (Audit Log) theo tiêu chuẩn bảo mật. Tự động ghi lại các hành động sửa đổi cấu hình nhạy cảm (cấu hình ngưỡng tồn kho SKU, đổi trạng thái người dùng), lưu trữ IP người dùng, thông tin thiết bị (User-Agent), thời gian và thông tin chi tiết trước/sau khi thay đổi phục vụ mục đích kiểm toán bảo mật.

### 🇺🇸 English Version
*   **Core WMS & Inventory Engine**: Designed and developed the end-to-end inbound and outbound warehouse logistics workflows, including Receiving Orders, Goods Receipt Notes (GRN), Putaway Tasks, Quality Control (QC) Inspections, Sales Orders, Stock Transfers, Picking Tasks, and inventory lot tracking (expiration/manufacturing dates) with real-time lot duplication prevention.
*   **Secure QR Scanner Pairing & OTP Auth**: Engineered a secure pairing mechanism to bind handheld mobile devices as barcode scanners. Designed a 2-factor authorization flow generating a `sessionId` on the web interface, dispatching a secure OTP via Gmail SMTP, storing it in Redis cache with TTL, and verifying it on mobile to issue temporary JWT scan tokens with restricted `SCANNER_TEMP` authority.
*   **Real-Time Barcode Scan Processing & Defect Classification**: Implemented the REST API to process real-time barcode scan events from mobile devices. Supported dual-condition classification (`PASS`/`FAIL`); automatically routed damaged items (`FAIL` with reason codes like `LEAK`, `TORN_PACKAGING`) to QC inspection, Quarantine Hold, and pushed real-time snapshot events to the manager's dashboard via Server-Sent Events (SSE).
*   **Excel Bulk Import Engine**: Built a bulk SKU master data import module from Excel spreadsheets (`.xlsx`) using Apache POI, validating files up to 5MB and 1000 rows. Developed an in-memory and database-level duplicate code detection algorithm to skip duplicate records and log row-by-row validation logs rather than rejecting the entire upload transaction.
*   **Dynamic Putaway Recommendation Engine**: Developed a capacity-aware putaway suggestions engine based on Category-to-Zone matching constraints (`Z-category_code`). Calculated bin capacities dynamically by checking real-time inventory snapshots against maximum bin weight thresholds, scoring and suggesting optimal bins, and handling bulk load splitting when capacities are met.
*   **Automated Invoice PDF Generation & Transaction Isolation**: Utilized iText to generate professional Landscape A4 packing slips (Phiếu Xuất Kho) with embedded Unicode Vietnamese font support. Implemented transactional isolation (`Propagation.REQUIRES_NEW`) for Cloudinary PDF uploads, ensuring that third-party network failures do not roll back the primary database order dispatch transactions.
*   **Mobile QR & Handwriting Signature Verification**: Created a delivery verification flow where drivers sign printed slips, scan a QR code via their phone, and upload a photographed image of the signed delivery note directly to Cloudinary (`signedNoteUrl`), automatically locking the sales order as `COMPLETED` to prevent post-dispatch data manipulation.
*   **Real-Time STOMP WebSocket Messaging**: Constructed a Spring WebSocket Broker (STOMP protocol) to deliver instant business notifications. Integrated JWT Bearer authentication during the STOMP connection handshake and configured target routing to broadcast alerts to specific roles (`/topic/notifications/{ROLE}`) or unicast messages to specific user mailboxes (`/user/queue/notifications`).
*   **Compliant System Audit Logging**: Integrated a centralized audit logging mechanism using Spring Security contexts to track sensitive configuration changes (e.g., SKU inventory threshold edits, user account toggling), recording client IP addresses, User-Agent headers, action types, targets, and actor user IDs for security compliance.

---

## 📦 Project Structure & Architecture

Dự án áp dụng các nguyên lý **Clean Architecture** kết hợp với **Domain-Driven Design (DDD)** nhằm chia nhỏ module, giữ cho các lớp phụ thuộc lỏng lẻo và dễ mở rộng.

```
SEP26Manager/
├── src/
│   ├── main/
│   │   ├── java/org/example/sep26management/
│   │   │   ├── domain/               # Domain Entities, Enums, Rules, & Engines (Không chứa framework phụ thuộc)
│   │   │   │   └── putaway/suggestion/   # Thuật toán cất hàng
│   │   │   ├── application/          # DTOs, Business Services, Constants, & Interfaces
│   │   │   │   ├── service/              # Logic nghiệp vụ (SkuService, IncidentService,...)
│   │   │   │   └── dto/                  # Requests & Responses
│   │   │   ├── infrastructure/       # Framework implementations (Spring Security, JPA, Redis, Mail, Cloudinary)
│   │   │   │   ├── config/               # Cấu hình WebSockets, Security, Cloudinary
│   │   │   │   ├── persistence/          # Database Entities & Repositories (JPA & Redis)
│   │   │   │   ├── security/             # JWT Filter, Token Provider
│   │   │   │   └── SseEmitterRegistry.java  # Quản lý Server-Sent Events
│   │   │   └── presentation/         # Controllers & REST Endpoints
│   │   │       └── controller/           # SKU, User, Scanner, Incident, Outbound Controllers
│   │   │   └── Sep26ManagementApplication.java  # Main entry point
│   │   └── resources/
│   │       ├── application.yml       # Cấu hình chính (dev, test, prod profiles)
│   │       └── application-prod.yml  # Cấu hình môi trường Production
├── backend/
│   ├── db/
│   │   └── init.sql                  # Database Schema khởi tạo ban đầu
│   ├── logs/                         # Nhật ký hệ thống (Local)
│   └── Dockerfile                    # Docker build configuration
├── docker-compose.yml                # Khởi tạo môi trường Docker (Postgres, Redis, Backend)
├── pom.xml                           # Quản lý Maven Dependencies
└── README.md                         # Tài liệu hướng dẫn này
```

---

## ⚡ Quick Start

### Môi trường yêu cầu (Prerequisites)
*   **Java 17+**
*   **Docker & Docker Compose**
*   **Maven** (Đã có sẵn trình bao `mvnw` trong project)

### Cài đặt nhanh (Local Development)

1.  **Clone repository & vào thư mục dự án**:
    ```bash
    git clone https://github.com/Quang2k3/SEP26Manager.git
    cd SEP26Manager
    ```

2.  **Thiết lập Môi trường**:
    Sao chép tệp tin cấu hình mẫu và chỉnh sửa các tham số của bạn (đặc biệt là JWT Secret, cấu hình Gmail SMTP và API Cloudinary):
    ```bash
    cp .env.example .env
    ```

3.  **Khởi động các dịch vụ phụ trợ (PostgreSQL & Redis) qua Docker**:
    Để thuận tiện phát triển và chạy ứng dụng cục bộ nhanh chóng, chạy lệnh:
    ```bash
    # Khởi động PostgreSQL và Redis
    docker-compose up -d postgres redis
    ```
    *(Nếu muốn chạy cả Spring Boot backend trong Docker Container, chạy lệnh `docker-compose up -d`)*

4.  **Chạy Backend Spring Boot**:
    Chạy trực tiếp dự án Spring Boot ở máy local để dễ dàng debug:
    ```bash
    ./mvnw spring-boot:run
    ```

5.  **Kiểm tra Trạng thái Hoạt động**:
    Kiểm tra dịch vụ Health Check:
    ```bash
    curl http://localhost:8080/actuator/health
    ```

---

## 🔧 Cấu hình Môi trường (.env & application.yml)

Hệ thống sử dụng các biến môi trường để cấu hình linh hoạt. Điền các giá trị vào tệp `.env` trước khi khởi động:

| Tên biến | Kiểu giá trị | Mô tả | Mặc định |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILE` | String | Profile hoạt động (`dev`, `prod`, `test`) | `dev` |
| `BACKEND_PORT` | Integer | Cổng chạy API Backend | `8080` |
| `POSTGRES_DB` | String | Tên cơ sở dữ liệu PostgreSQL | `SEP26WMS` |
| `POSTGRES_USER` | String | Tài khoản quản trị cơ sở dữ liệu | `postgres` |
| `POSTGRES_PASSWORD`| String | Mật khẩu cơ sở dữ liệu | `123` |
| `POSTGRES_PORT` | Integer | Cổng kết nối PostgreSQL | `5432` |
| `REDIS_HOST` | String | Địa chỉ Redis server | `localhost` |
| `REDIS_PORT` | Integer | Cổng kết nối Redis | `6379` |
| `JWT_SECRET` | String | Khóa bảo mật JWT (Yêu cầu HS256 256-bit) | *Bắt buộc thay đổi* |
| `GMAIL_USERNAME` | String | Tài khoản email gửi thông báo & OTP | *Nhập Gmail của bạn* |
| `GMAIL_APP_PASSWORD`| String | Mật khẩu ứng dụng của Gmail (App Password)| *Mật khẩu ứng dụng* |
| `CLOUDINARY_CLOUD_NAME`| String| Tên Cloud name tài khoản Cloudinary | *Nhập từ Cloudinary* |
| `CLOUDINARY_API_KEY` | String | API Key của Cloudinary | *Nhập từ Cloudinary* |
| `CLOUDINARY_API_SECRET`| String| API Secret của Cloudinary | *Nhập từ Cloudinary* |
| `FRONTEND_URL` | String | Địa chỉ FE để cấu hình bảo mật CORS | `http://localhost:3000` |

---

## 📡 API Endpoints Index

### 🔓 Xác thực & Tài khoản (`/v1/auth`)
*   `POST /v1/auth/login`: Đăng nhập, nhận Access Token JWT (8 giờ) và Refresh Token.
*   `POST /v1/auth/refresh`: Sử dụng Refresh Token để gia hạn phiên đăng nhập.
*   `POST /v1/auth/logout`: Đăng xuất, hủy phiên làm việc.

### 👥 Quản lý Người dùng (`/v1/users` - Yêu cầu vai trò: `MANAGER`)
*   `POST /v1/users/create-user`: Tạo tài khoản mới cho thủ kho/QC, tự động gửi mật khẩu tạm qua Email.
*   `GET /v1/users/list-users`: Lấy danh sách thành viên trong kho (Có lọc keyword, trạng thái, phân trang).
*   `PUT /v1/users/{userId}/assign-role`: Phân quyền vai trò mới (`MANAGER`, `KEEPER`, `QC`).
*   `PUT /v1/users/{userId}/change-status`: Bật/Tắt trạng thái hoạt động của tài khoản (Hỗ trợ tạm ngưng kèm lý do).

### 📦 Quản lý SKU & Tồn kho (`/v1/skus`)
*   `GET /v1/skus/{skuId}`: Xem chi tiết SKU (Bao gồm thông tin quy cách, thương hiệu, danh mục, hình ảnh).
*   `GET /v1/skus/search`: Tìm kiếm SKU theo mã/tên (partial & case-insensitive), trả kèm số lượng tồn khả dụng thực tế.
*   `PUT /v1/skus/{skuId}/threshold`: Thiết lập ngưỡng tồn kho Min/Max cho sản phẩm trong kho (Yêu cầu vai trò: `MANAGER`).
*   `GET /v1/skus/{skuId}/threshold`: Xem ngưỡng tồn kho hiện tại của SKU.
*   `POST /v1/skus/import`: Nhập hàng loạt SKU từ tệp Excel (Yêu cầu vai trò: `MANAGER`).
*   `GET /v1/skus/barcode/{barcode}`: Tra cứu thông tin SKU nhanh bằng quét mã vạch sản phẩm.
*   `GET /v1/skus/barcode/{barcode}/locations`: Tìm kiếm các BIN đang chứa sản phẩm này.
*   `GET /v1/skus/stock-summary`: Xem tổng hợp tồn kho của tất cả SKU dạng biểu đồ.
*   `PATCH /v1/skus/{skuId}/image`: Cập nhật ảnh minh họa cho SKU.

### 📱 Tích hợp Máy quét Cầm tay (`/v1/scanner-otp` & `/v1/scan-events`)
*   `POST /v1/scanner-otp/generate`: (Web UI) Tạo session quét và sinh mã OTP gửi đến Email (Yêu cầu vai trò: `KEEPER`, `QC`).
*   `POST /v1/scanner-otp/verify`: (Mobile) Xác thực OTP và cấp JWT `scannerToken` cho điện thoại quét.
*   `POST /v1/scanner-otp/cleanup`: Xóa phiên OTP khỏi Redis sau khi kết nối thành công.
*   `POST /v1/scan-events`: (Mobile) Thiết bị gửi sự kiện quét mã vạch sản phẩm (Hỗ trợ gửi `PASS` hoặc `FAIL` kèm `reasonCode`).
*   `DELETE /v1/scan-events`: Hủy/giảm số lượng sản phẩm quét nhầm trong phiên làm việc.

### ⚠️ Quản lý Sự cố & Lệch kho (`/v1/incidents`)
*   `POST /v1/incidents`: (Thủ kho) Khai báo sự cố phát sinh tại cổng kiểm check xe hoặc trong lúc nhận/nhặt hàng.
*   `GET /v1/incidents`: Danh sách sự cố (Lọc theo status, category, SO, Receiving, reportedBy).
*   `PUT /v1/incidents/{id}/approve`: (Manager) Phê duyệt sự cố cổng check xe, cho phép dỡ hàng.
*   `PUT /v1/incidents/{id}/reject`: (Manager) Từ chối nhận xe hàng, đuổi xe về.
*   `PUT /v1/incidents/{id}/resolve`: (Manager) Phán quyết xử lý lô hàng lỗi QC (Chấp nhận nhận hàng/Trả hàng về nhà cung cấp).
*   `PUT /v1/incidents/{id}/resolve-discrepancy`: (Manager) Xử lý thừa/thiếu số lượng (Close thiếu, Giao bù PO, nhận thừa, hoàn trả thừa). Tự động sinh PO giao bù phụ (`-B` suffix) nếu chọn chờ giao bù.

### 🚚 Quản lý Xuất kho & Giao nhận (`/v1/outbound`)
*   `GET /v1/outbound/pick-list/by-document/{documentId}`: Xem danh sách nhặt hàng (Pick list) của đơn hàng.
*   `GET /v1/outbound/sales-orders/{soId}/dispatch-pdf`: Xuất/Lấy link tải file PDF Phiếu Xuất Kho từ Cloudinary.
*   `POST /v1/outbound/sales-orders/{soId}/signed-note`: (Mobile) Quét QR trên phiếu, chụp ảnh phiếu ký tay và upload lên Cloudinary để đóng đơn hàng `COMPLETED`.

---

## 📈 Quy trình Nghiệp vụ Nổi bật (Core Logistics Workflows)

### 1. Quy trình Kết nối Thiết bị Quét (Pairing Scanner Flow)
```
[Trình duyệt Web]                   [Hệ thống Backend]                   [Email / Redis]                  [Thiết bị Di động]
      │                                     │                                    │                                 │
      │ ── 1. Yêu cầu kết nối (JWT) ──────> │                                    │                                 │
      │                                     │ ── 2. Sinh OTP + Gửi Email ──────> │ ── (Mã OTP 6 số) ─────────────> │
      │                                     │ ── 3. Lưu OTP vào Redis (TTL) ───> │                                 │
      │ <─ 4. Trả về SessionID ──────────── │                                    │                                 │
      │                                     │                                    │                                 │
      │ (FE Tạo QR Code từ SessionID)       │                                    │                                 │
      │                                     │                                    │                                 │
      │                                     │                                    │ ── 5. Quét QR trên Web ───────> │
      │                                     │                                    │ ── 6. Nhập OTP nhận từ Email ─> │
      │                                     │ <─ 7. Gửi SessionID + OTP ────────────────────────────────────────── │
      │                                     │ ── 8. Xác thực bằng Redis ───────> │                                 │
      │                                     │ ── 9. Cấp JWT ScannerToken ───────────────────────────────────────> │
```

### 2. Quy trình Quét Nhận Hàng QC Lỗi & Xử lý Giao Bù (Shortage Resolution Flow)
```
[Thủ kho / Thiết bị Quét]            [Hệ thống Backend]                  [Manager Dashboard]                 [Hệ thống PO]
           │                                 │                                    │                               │
           │ ── 1. Quét barcode (thiếu hụt) >│                                    │                               │
           │                                 │ ── 2. Đẩy thông báo Event qua SSE >│                               │
           │ ── 3. Kết thúc phiên quét ─────>│                                    │                               │
           │                                 │ ── 4. Tự tạo sự cố SHORTAGE ──────>│                               │
           │                                 │                                    │                               │
           │                                 │                                    │ ── 5. Review & Chọn Giao bù ─ │
           │                                 │ <─ 6. Gọi /resolve-discrepancy ────│                               │
           │                                 │ ── 7. Xác nhận chốt thực nhận ───> │ (Cập nhật PO hiện tại)        │
           │                                 │ ── 8. Sinh PO mới Giao bù (-B) ──────────────────────────────────> │
```

---

## 🧪 Quy trình Chạy Kiểm thử (Testing)

Dự án tích hợp đầy đủ bộ kiểm thử tự động JUnit 5 và JaCoCo để theo dõi mức độ bao phủ (Coverage).

```bash
# 1. Chạy toàn bộ unit tests
mvn test

# 2. Chạy một bài kiểm tra cụ thể
mvn test -Dtest=SkuServiceTest

# 3. Chạy kiểm thử tích hợp (Integration Tests)
mvn verify -Pintegration-tests

# 4. Xuất báo cáo JaCoCo (Coverage Report)
# Báo cáo sẽ được xuất ra thư mục: target/site/jacoco/index.html
mvn jacoco:report
```

---

## 🚨 Hướng dẫn Xử lý Sự cố Cực nhanh (Troubleshooting)

### Lỗi 1: Không kết nối được Cơ sở dữ liệu (`Database connection failed`)
*   **Nguyên nhân**: PostgreSQL trên máy host chưa được bật, sai IP cấu hình hoặc Docker container chưa liên kết mạng.
*   **Cách xử lý**:
    1.  Kiểm tra xem Postgres có đang chạy không: `docker ps`
    2.  Kiểm tra địa chỉ IP cấu hình trong biến `SPRING_DATASOURCE_URL` ở tệp `.env`. Đối với Docker trên Windows chạy cục bộ, sử dụng IP thật của máy hoặc cấu hình `jdbc:postgresql://host.docker.internal:5432/SEP26WMS`.
    3.  Thử ping db: `docker exec -it sep26manager-postgres pg_isready -U postgres`

### Lỗi 2: Không thể nhận OTP hoặc xác thực thiết bị quét (`Redis connection failed`)
*   **Nguyên nhân**: Redis chưa được khởi động hoặc cổng `6379` bị chiếm dụng bởi service khác.
*   **Cách xử lý**:
    1.  Chạy lệnh `docker ps` để đảm bảo container `sep26manager-redis` đang hoạt động.
    2.  Kiểm tra logs Redis: `docker-compose logs redis`
    3.  Truy cập Redis CLI để kiểm tra keys:
        ```bash
        docker exec -it sep26manager-redis redis-cli
        127.0.0.1:6379> KEYS *
        ```

### Lỗi 3: Trùng cổng 8080 (`Port 8080 already in use`)
*   **Nguyên nhân**: Có ứng dụng Java, Node.js hoặc Docker chạy ngầm chiếm cổng `8080`.
*   **Cách xử lý**:
    1.  Tìm Process ID đang chiếm cổng:
        ```powershell
        netstat -ano | findstr :8080
        ```
    2.  Tắt tiến trình chiếm dụng (Thay thế `<PID>` bằng số tìm thấy ở lệnh trên):
        ```powershell
        taskkill /PID <PID> /F
        ```
    3.  Hoặc thay đổi biến `BACKEND_PORT` trong tệp `.env` thành cổng khác (ví dụ: `8081`).

---

## 📋 Pre-Production Checklist (Danh sách kiểm tra trước khi bàn giao)

Trước khi đóng gói triển khai lên môi trường chạy thực tế (Production), hãy chắc chắn đã hoàn thành các bước sau:

- [ ] Toàn bộ các kiểm thử tự động đều đã vượt qua thành công (`mvn clean test` trả về thành công).
- [ ] Tệp tin cấu hình môi trường `.env` đã được điền đầy đủ và bảo mật (Thay đổi `JWT_SECRET` sang khóa ngẫu nhiên dài 256-bit).
- [ ] Database Schema đã được cập nhật chính xác (Chạy các tệp lệnh migrations nếu có).
- [ ] Cấu hình CORS `FRONTEND_URL` trùng khớp chính xác với tên miền Frontend hoạt động.
- [ ] Các thông tin kết nối Cloudinary và Gmail App Password đã được ẩn và cấu hình dạng biến môi trường bảo mật trên VPS.
- [ ] Kiểm tra các dịch vụ SSE và WebSocket hoạt động mượt mà khi đi qua Cloudflare Tunnel hoặc Nginx Proxy (Cấu hình Nginx cho phép giữ kết nối Upgrade WebSocket).

---

**SEP26 Backend Team** 🚀
