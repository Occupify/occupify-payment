# Kiến Trúc Microservice Toàn Hệ Sinh Thái (Occupify Ecosystem Architecture)

> **Phạm vi tài liệu**: Áp dụng chung cho tất cả backend microservices trong hệ sinh thái Occupify (`occupify-identity`, `occupify-core`, `occupify-payment`, `occupify-notification`, `occupify-admin`).  
> **Mục đích**: File tài liệu kiến trúc chuẩn hóa dùng chung (Shared Architecture Blueprint). Khi khởi tạo bất kỳ microservice mới nào, chỉ cần copy file này sang thư mục `.agent/` của service đó để kế thừa toàn bộ quy chuẩn kiến trúc, API gateway, response envelopes và exception handling.

---

## 1. Bản Đồ Hệ Sinh Thái & Phân Bổ Cổng (Service Ports)

Hệ thống Occupify bao gồm 1 API Gateway tập trung và 5 backend microservices tương ứng với 5 Bounded Contexts:

| Dịch vụ | Bounded Context | Cổng Mặc Định | Cơ Sở Dữ Liệu | Nhiệm vụ chính |
|:---|:---|:---|:---|:---|
| **`occupify-gateway`** | **API Gateway** | `8180` | None (Stateless) | Entrypoint duy nhất: Định tuyến, Xác thực JWT, Rate-limit, Circuit Breaker |
| **`occupify-identity`** | **Identity** | `8181` | `occupify_id` | Người dùng, Xác thực tài khoản, Profile, Kỹ năng, Học vấn, Kinh nghiệm |
| **`occupify-core`** | **Core** | `8182` | `occupify_core` | Thị trường việc làm, Dự án, Tuyển dụng, Chào thầu (Bids), Lời mời (Offers), Đánh giá |
| **`occupify-payment`** | **Payment** | `8183` | `occupify_payment`| Hợp đồng, Hủy hợp đồng, Ví điện tử, Ký quỹ Escrow, Sổ cái giao dịch |
| **`occupify-notification`**| **Notification** | `8184` | `occupify_notif` | Thông báo thời gian thực in-app, WebPush, Email alert |
| **`occupify-admin`** | **Administration**| `8185` | `occupify_admin` | Kiểm duyệt báo cáo vi phạm, Cờ cảnh báo tài khoản, Nhật ký kiểm toán |

---

## 2. Chuẩn Tích Hợp API Gateway (`occupify-gateway`)

Mọi giao tiếp từ Client (Web/Mobile) đều đi qua `occupify-gateway` trước khi tới các microservice downstream:

```
[Client (Web / Mobile)]
       │
       ▼ (HTTPS / JSON)
[occupify-gateway (Port 8180)]
       │
       ├── CorrelationIdFilter: Tự động cấp phát / bảo toàn X-Correlation-Id
       ├── AuthenticationGatewayFilter:
       │     • Xác thực chữ ký & hạn dùng JWT Bearer tập trung
       │     • Loại bỏ header giả mạo từ client (stripIdentityHeaders)
       │     • Inject các headers định danh tin cậy vào downstream:
       │         - X-User-Id: UUID của user
       │         - X-User-Email: Email của user
       │         - X-User-Role: FREELANCER | CLIENT | ADMIN
       ├── Resilience4j Gateway Filters:
       │     • Retry (3 lần với GET/HEAD khi gặp 502, 503, 504)
       │     • CircuitBreaker tương ứng cho từng service (VD: paymentCircuitBreaker)
       │
       ▼ (Internal HTTP)
[Downstream Microservice]
       │
       └── Security/UserContextFilter (Đọc X-User-* đưa vào UserContextHolder & SLF4J MDC)
```

### Nguyên tắc xử lý Identity tại Microservice Downstream:
1. **Không lặp lại xác thực JWT**: Microservice nội bộ không cần verify chữ ký cryptographic của JWT khi nhận request từ Gateway, giúp giảm latency.
2. **UserContextHolder**: Mọi microservice đều có `UserContextFilter` để đọc `X-User-Id`, `X-User-Email`, `X-User-Role` và lưu vào `ThreadLocal UserContextHolder`.
3. **Correlation ID Tracing**: Mọi log trong service đều được gán `X-Correlation-Id` thông qua SLF4J MDC (`%X{correlationId}`).
4. **Không tin tưởng User ID từ Client Body**: Khi thực hiện các thao tác người dùng (tạo hợp đồng, nạp tiền, gửi báo cáo), luôn lấy ID người dùng từ `UserContextHolder.getUserId()`, tuyệt đối không lấy từ JSON body.

---

## 3. Cấu Trúc Phân Tầng Chuẩn (Clean Layered Package Structure)

Mọi Spring Boot 3 microservice trong hệ sinh thái đều tuân theo cấu trúc package chuẩn:

```
src/main/java/com/occupify/<service>/
├── config/                  # Spring @Configuration (Security, OpenAPI, CORS, Jackson, WebClient)
├── constant/                # Route paths, regex patterns, error codes, message keys, event topics
├── controller/              # REST Controllers (kế thừa AbstractBaseController, dùng ResponseFactory)
├── dto/
│   ├── request/             # Inbound Request DTOs (Java records với Jakarta @Valid)
│   └── response/            # Outbound Feature Response DTOs (Java records)
│       └── base/            # Envelopes chuẩn: AbstractBaseResponse, SingleResponse, PageResponse, ErrorResponse
├── entity/                  # JPA Entities ánh xạ 1:1 với các bảng nội bộ (kèm Audit timestamps)
├── enums/                   # Strongly typed Java Enums
├── event/                   # Event schemas, Kafka/RabbitMQ producers & consumers (Saga)
├── exception/               # BaseAppException, AppErrorCode enum, AppExceptionHandler (@RestControllerAdvice)
├── mapper/                  # MapStruct component mappers (Entity <-> DTO)
├── repository/              # Spring Data JPA repositories (Pessimistic lock, projections, JOIN FETCH)
├── scheduler/               # @Scheduled background cron jobs
├── security/                # UserContextFilter, UserContextHolder, SecurityEvaluator
├── service/                 # Interfaces nghiệp vụ cốt lõi
│   └── impl/                # Implementations có @Transactional
└── util/                    # Stateless helper utilities
```

---

## 4. Chuẩn Hóa Response & Exception Handling (Envelope Pattern)

Toàn bộ hệ sinh thái Occupify áp dụng thống nhất tài liệu đặc tả `docs/Response-ErrorHandling-Architecture.md`:

### 4.1. Hệ thống Response DTOs (`dto.response.base`)

1. **`AbstractBaseResponse`**: Lớp cơ sở chứa `int httpStatus` và `String message`.
2. **`SingleResponse<T>`**: Phản hồi đơn lẻ chứa dữ liệu, kế thừa `AbstractBaseResponse`.
   - Thứ tự JSON: `{"httpStatus": 200, "message": "...", "data": { ... }}`
3. **`SuccessResponse`**: Phản hồi thành công không mang body (áp dụng cho các Command API như delete, cancel, logout).
   - Thứ tự JSON: `{"httpStatus": 200, "message": "..."}`
4. **`PageResponse<T>`**: DTO chuẩn hóa phân trang (đã khử rò rỉ metadata nội bộ của Spring Data `PageImpl`):
   - Chứa: `items` (List), `page`, `size`, `totalElements`, `totalPages`, `last`.
   - Luôn được bọc trong `SingleResponse<PageResponse<T>>`.
5. **`ErrorResponse`**: Phản hồi lỗi nghiệp vụ:
   - Chứa: `httpStatus`, `errorCode`, `message`, `path`, `timestamp`.
6. **`DetailedErrorResponse`**: Phản hồi lỗi validation form/field:
   - Kế thừa/mở rộng `ErrorResponse`, bổ sung `Map<String, List<String>> items` liệt kê chi tiết các vi phạm theo từng trường dữ liệu.

### 4.2. `ResponseFactory` Component
Spring Bean `@Component` duy nhất chịu trách nhiệm đóng gói toàn bộ `ResponseEntity`:

```java
@Component
public class ResponseFactory {
    public ResponseEntity<SuccessResponse> success(String message);
    public ResponseEntity<SuccessResponse> success(HttpStatus status, String message);
    public <T> ResponseEntity<SingleResponse<T>> success(T data, String message);
    public <T> ResponseEntity<SingleResponse<T>> success(HttpStatus status, T data, String message);
    public <T> ResponseEntity<SingleResponse<PageResponse<T>>> successPage(Page<T> pageData, String message);
    public ErrorResponse createErrorResponse(HttpStatus status, String errorCode, String message, String path);
    public DetailedErrorResponse createDetailedErrorResponse(HttpStatus status, String errorCode, String message, String path, Map<String, List<String>> items);
}
```

### 4.3. `AbstractBaseController`
Mọi REST Controller bắt buộc kế thừa `AbstractBaseController` và sử dụng Setter Injection:

```java
public abstract class AbstractBaseController {
    protected ResponseFactory responseFactory;

    @Autowired
    public void setResponseFactory(ResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    protected <T> ResponseEntity<SingleResponse<T>> created(T data, String message) {
        return responseFactory.success(HttpStatus.CREATED, data, message);
    }
    protected <T> ResponseEntity<SingleResponse<T>> success(T data, String message) {
        return responseFactory.success(data, message);
    }
    protected ResponseEntity<SuccessResponse> success(String message) {
        return responseFactory.success(message);
    }
    protected <T> ResponseEntity<SingleResponse<PageResponse<T>>> successPage(Page<T> pageData, String message) {
        return responseFactory.successPage(pageData, message);
    }
}
```

### 4.4. Cấu Trúc Ngoại Lệ & `@RestControllerAdvice`
- **`BaseAppException`**: Ngoại lệ cơ sở mang `AppErrorCode` (interface định nghĩa `getHttpStatus()`, `getCode()`, `getMessage()`) và `Object[] args`.
- **`AppExceptionHandler` (`@RestControllerAdvice`)**:
  - Bắt `BaseAppException`: Chuyển thành `ErrorResponse`.
  - Bắt `MethodArgumentNotValidException` & `ConstraintViolationException`: Chuyển thành `DetailedErrorResponse`.
  - Bắt các lỗi chưa lường trước (`Exception.class`): Ghi log kèm Correlation ID và trả về lỗi 500 tổng quát, không làm rò rỉ stack trace.

---

## 5. Nguyên Tắc Dữ Liệu & Giao Tiếp Phân Tán (Distributed Data & Events)

1. **Database-per-Service**:
   - Mỗi microservice sở hữu CSDL riêng biệt.
   - Tuyệt đối cấm cross-database joins hoặc cấu hình datasource trỏ vào DB của service khác.
2. **Khóa Ngoại Logic (Logical Foreign Keys)**:
   - Các bảng chỉ tạo Physical FK nội bộ.
   - Liên kết sang entity của service khác được lưu bằng trường `UUID` logic (ví dụ `user_id`, `employer_id`, `project_role_id`).
3. **Kiến Trúc Hướng Sự Kiện & Saga Pattern**:
   - Sử dụng Kafka hoặc RabbitMQ với mô hình **Saga Choreography Pattern** để điều phối giao dịch phân tán.
   - Bắt buộc áp dụng **Transactional Outbox Pattern** hoặc xuất bản event sau khi commit DB thành công.
   - Mọi Consumer phải là **Idempotent Consumer** (chống xử lý trùng lặp tin nhắn).
4. **Mô Hình Người Dùng Hai Vai Trò (Dual-Role User Model: EMPLOYEE và EMPLOYER là một người)**:
   - Người dùng sử dụng chung một tài khoản đăng nhập (`users.id`) và cùng một giao diện duy nhất để chuyển đổi giữa Freelancer Workspace (vai trò `EMPLOYEE`) và Client Workspace (vai trò `EMPLOYER`).
   - Mỗi người dùng chỉ có **duy nhất 1 ví tiền** (`wallets.user_id = users.id`). Ví này vừa tích lũy thu nhập từ việc làm freelancer, vừa dùng để thanh toán ký quỹ khi thuê freelancer khác.
   - Ràng buộc bất biến toàn hệ sinh thái: Tuyệt đối cấm tự ký hợp đồng với chính mình (`contracts.employer_id <> contracts.employee_id`).
---

## 6. Chiến Lược Quản Lý Database Migration Với Flyway

- Thư mục migrations: `src/main/resources/db/migration/`
- Định dạng đặt tên bắt buộc: `V{sequential_number}__{snake_case_description}.sql` (ví dụ `V1__init_schema.sql`, `V2__add_index.sql`). **Nghiêm cấm dùng timestamp**.
- **Quy tắc An Toàn Migration**:
  - Không bao giờ chạy các câu lệnh phá hủy (`DROP TABLE`, `DROP COLUMN`) trực tiếp trên môi trường Production; sử dụng mô hình *Expand and Contract*.
  - Chỉ chứa migration DDL và System Master Data cần thiết (bảng danh mục hệ thống).
  - Nghiêm cấm đưa dữ liệu người dùng thật hoặc thông tin nhạy cảm vào script migration.
