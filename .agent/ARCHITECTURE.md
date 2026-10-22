# Kiến Trúc Dịch Vụ Thanh Toán (Payment Service Architecture)

> **Dịch vụ**: `occupify-payment`  
> **Bounded Context**: **Payment**  
> **Runtime**: Spring Boot 3.3.4, Java 25, PostgreSQL 16 (Alpine), Flyway, MapStruct  
> **Cổng Dịch Vụ**: `8183` | **Cổng Gateway**: `8180` | **Tên CSDL**: `occupify_payment`  
> **Tài liệu chuẩn kiến trúc dùng chung**: Xem **[ECOSYSTEM-ARCHITECTURE.md](ECOSYSTEM-ARCHITECTURE.md)** để nắm toàn bộ quy chuẩn chung của hệ sinh thái Occupify (API Gateway, Response/Exception Envelopes, Saga, Layering, Flyway).

---

## 1. Cấu Trúc Thư Mục Dịch Vụ `occupify-payment`

```
occupify-payment/
├── .agent/
│   ├── ECOSYSTEM-ARCHITECTURE.md   # Chuẩn kiến trúc Microservices toàn hệ sinh thái (DÙNG CHUNG)
│   ├── ARCHITECTURE.md             # Đặc tả kiến trúc chuyên biệt cho Payment Service (FILE NÀY)
│   ├── OCCUPIFY.md                 # Tầm nhìn sản phẩm, freelancer-first domain briefing
│   └── README.md                   # Hướng dẫn cấu hình AI Agent
├── docs/
│   ├── ECOSYSTEM-ERD.md            # Sơ đồ CSDL và schema toàn hệ sinh thái 5 Bounded Contexts (DÙNG CHUNG)
│   ├── ERD.md                      # Đặc tả chi tiết CSDL của riêng Payment Service (4 bảng + Invariants)
│   └── Response-ErrorHandling-Architecture.md # Quy chuẩn Response & Error Envelopes của toàn dự án
├── src/
│   ├── main/
│   │   ├── java/com/occupify/payment/
│   │   │   ├── config/             # Spring Beans: SecurityConfig, OpenAPIConfig, JacksonConfig, WebClientConfig
│   │   │   ├── constant/           # Route constants, Regex, Error code prefixes, Kafka/RabbitMQ Topics
│   │   │   ├── controller/         # REST Controllers: ContractController, WalletController, PaymentWebhookController
│   │   │   ├── dto/
│   │   │   │   ├── request/        # Request records: CreateContractRequest, DepositRequest, CancelContractRequest
│   │   │   │   └── response/       # Feature Response records: ContractResponse, WalletResponse, TransactionResponse
│   │   │   │       └── base/       # Envelope classes chuẩn: SingleResponse, PageResponse, ErrorResponse, etc.
│   │   │   ├── entity/             # 4 JPA Entities: Contract, CancellationContract, Wallet, Transaction
│   │   │   ├── enums/              # Domain Enums: ContractStatus, PaymentStatus, TransactionType, PaymentMethod, etc.
│   │   │   ├── event/              # Event schemas, Publishers & Consumers (Saga Choreography)
│   │   │   ├── exception/          # PaymentErrorCode (enum), PaymentException, AppExceptionHandler
│   │   │   ├── mapper/             # MapStruct interfaces: ContractMapper, WalletMapper, TransactionMapper
│   │   │   ├── repository/         # Spring Data JPA: WalletRepository (với Pessimistic Lock), ContractRepository
│   │   │   ├── scheduler/          # EscrowTimeoutScheduler, PaymentReconciliationScheduler
│   │   │   ├── security/           # UserContextFilter, UserContextHolder, PaymentSecurityEvaluator
│   │   │   ├── service/            # Interfaces: ContractService, WalletService, TransactionService, EscrowService
│   │   │   │   └── impl/           # @Transactional Service Implementations
│   │   │   ├── util/               # MoneyMath, PaymentSignatureVerifier (HMAC-SHA512), VietQRBuilder
│   │   │   └── OccupifyPaymentApplication.java
│   │   └── resources/
│   │       ├── application.yml     # Cấu hình Spring Boot (Port 8183, CSDL occupify_payment, Actuator)
│   │       └── db/migration/       # Flyway SQL scripts: V1__init_payment_schema.sql
│   └── test/                       # Unit tests, slice tests (@DataJpaTest, @WebMvcTest), integration tests
├── docker-compose.yml              # PostgreSQL 16 Alpine container (Cổng 5532 -> 5432)
├── Dockerfile                      # Multi-stage production container build
├── Makefile                        # Lifecycle runner (Docker & Podman auto-detected)
├── pom.xml                         # Maven project dependencies
├── AGENT.md                        # Sổ tay vận hành cho AI Agent & Kỹ sư
└── README.md                       # Hướng dẫn khởi chạy dự án
```

---

## 2. Tích Hợp API Gateway Với `occupify-payment`

Theo thiết kế tại `occupify-gateway` (Port `8180`), các luồng truy cập vào `occupify-payment` (Port `8183`) được cấu hình như sau:

```yaml
# Trích xuất từ occupify-gateway/src/main/resources/application.yml
- id: payment-service
  uri: ${SERVICES_PAYMENT_URL:http://localhost:8183}
  predicates:
    - Path=/contracts/**, /wallets/**, /payments/**
  filters:
    - name: Retry
      args:
        retries: 3
        statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
        methods: GET, HEAD
    - name: CircuitBreaker
      args:
        name: paymentCircuitBreaker
        fallbackUri: forward:/fallback/payment
```

### Luồng Xử Lý Header & Xác Thực:
1. **Gateway chuyển tiếp**: Gateway xác thực token JWT, sau đó inject vào HTTP request gửi sang `occupify-payment`:
   - `X-User-Id`: UUID của người dùng hiện tại.
   - `X-User-Email`: Email người dùng.
   - `X-User-Role`: `FREELANCER`, `CLIENT`, hoặc `ADMIN`.
   - `X-Correlation-Id`: UUID định danh luồng xử lý (phục vụ distributed tracing).
2. **`UserContextFilter` tại Payment Service**:
   - Chặn request tại tầng Filter, đọc các headers trên đưa vào `UserContextHolder` (ThreadLocal).
   - Đưa `X-Correlation-Id` vào SLF4J MDC để hiển thị trên từng dòng log.
3. **Kiểm tra quyền sở hữu & Mô hình 2 vai trò (Transitive Ownership & Dual-Role Model)**:
   - **Mô hình Một Tài Khoản - Hai Vai Trò**: Người dùng sử dụng cùng 1 tài khoản đăng nhập để thao tác trên cả 2 workspace (Freelancer & Client). Do đó, một người dùng có thể là `employer` ở hợp đồng này và là `employee` ở hợp đồng khác.
   - **Ví duy nhất**: Các API truy vấn/thao tác trên ví (`/wallets/**`): Kiểm tra `wallet.getUserId().equals(UserContextHolder.getUserId())`. Ví duy nhất này phục vụ cả nhận thù lao (khi làm việc) và chi trả ký quỹ (khi thuê người).
   - **API hợp đồng (`/contracts/**`)**: Kiểm tra quyền theo ngữ cảnh hợp đồng: `contract.getEmployerId().equals(userId) || contract.getEmployeeId().equals(userId)`.
   - **Ràng buộc cấm tự ký hợp đồng**: Hệ thống bắt buộc `employer_id != employee_id`, ném `InvalidContractStateException` nếu phát hiện trùng lặp.
---

## 3. Đặc Tả Triển Khai Các Tầng (Package-by-Package Specifics)

### 3.1. Tầng `controller/`
- Mọi Controller (`ContractController`, `WalletController`, `CancellationController`, `PaymentWebhookController`) đều kế thừa `AbstractBaseController`.
- Không inject Repository vào Controller; chỉ inject Service.
- Sử dụng `ResponseFactory` để trả về các Envelope chuẩn:
  - API lấy chi tiết: `return success(contractResponse, "Lấy thông tin hợp đồng thành công");`
  - API tạo mới: `return created(createdContractResponse, "Tạo hợp đồng thành công");`
  - API lệnh (delete, cancel): `return success("Đã gửi yêu cầu hủy hợp đồng thành công");`
  - API phân trang: `return successPage(contractPage, "Lấy danh sách hợp đồng thành công");`

### 3.2. Tầng `entity/` (4 Thực Thể Core Bounded Context Payment)
1. **`Contract`** (bảng `contracts`):
   - Quản lý điều khoản hợp đồng giữa Employer và Employee.
   - Các trường khóa logic: `employer_id (UUID)`, `employee_id (UUID)`, `project_role_id (UUID)`, `source_id (UUID)`.
   - Ràng buộc bất biến: `employer_id != employee_id` (cấm tự thuê chính mình).
   - Ràng buộc JPA: `@OneToMany(mappedBy = "contract", fetch = FetchType.LAZY) private List<Transaction> transactions;`
   - Audit: `@CreatedDate private Instant createdAt;`, `@LastModifiedDate private Instant updatedAt;`.
2. **`CancellationContract`** (bảng `cancellation_contracts`):
   - Quản lý tranh chấp và yêu cầu hủy.
   - Quan hệ: `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id") private Contract contract;`
   - Khóa logic: `requested_by_user_id (UUID)`.
   - Audit: `createdAt`, `updatedAt`.
3. **`Wallet`** (bảng `wallets`):
   - Quản lý số dư người dùng.
   - Khóa logic: `user_id (UUID, unique = true)`.
   - Số dư: `availableBalance (BigDecimal)`, `lockedBalance (BigDecimal)`.
   - Audit: `createdAt`, `updatedAt`.
   - Phiên bản khóa lạc quan (tùy chọn kết hợp): `@Version private Long version;`.
4. **`Transaction`** (bảng `transactions`):
   - Sổ cái bất biến ghi nhận mọi biến động tiền tệ.
   - Quan hệ: `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "wallet_id") private Wallet wallet;`, `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id") private Contract contract;`.
   - Audit: `createdAt`, `updatedAt`.

### 3.3. Tầng `repository/` & Chiến Lược Khóa Concurrency
- `WalletRepository`: Bắt buộc cung cấp phương thức khóa bi quan ghi (`Pessimistic Write Lock`) khi thao tác số dư:
  ```java
  public interface WalletRepository extends JpaRepository<Wallet, UUID> {
      @Lock(LockModeType.PESSIMISTIC_WRITE)
      @Query("SELECT w FROM Wallet w WHERE w.id = :id")
      Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

      @Lock(LockModeType.PESSIMISTIC_WRITE)
      @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
      Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
  }
  ```

### 3.4. Tầng `service/` & Vòng Đời Ký Quỹ (Escrow Lifecycle)
Mọi phương thức biến động số dư trong `WalletService` và `EscrowService` phải chạy trong `@Transactional`:
1. **Khóa Ký Quỹ (`lockEscrow(UUID contractId, BigDecimal amount)`)**:
   - Lock ví của Employer qua `findByUserIdForUpdate`.
   - Kiểm tra `availableBalance >= amount`. Nếu không đủ, ném `InsufficientBalanceException`.
   - Trừ `availableBalance`, cộng vào `lockedBalance`.
   - Ghi bản ghi `Transaction` loại `ESCROW_HOLD`.
   - Cập nhật `contract.paymentStatus = 'HELD_IN_ESCROW'`, `contract.status = 'IN_PROGRESS'`.
2. **Giải Ngân Cho Freelancer (`releaseEscrow(UUID contractId)`)**:
   - Lock đồng thời ví Employer và ví Freelancer.
   - Trừ `lockedBalance` của Employer, cộng vào `availableBalance` của Freelancer (sau khi trừ phí nền tảng nếu có).
   - Ghi bản ghi `Transaction` loại `ESCROW_RELEASE`.
   - Cập nhật `contract.paymentStatus = 'PAID'`, `contract.status = 'COMPLETED'`.
3. **Hoàn Tiền Hủy Hợp Đồng (`refundEscrow(UUID contractId)`)**:
   - Lock ví Employer.
   - Chuyển tiền từ `lockedBalance` về lại `availableBalance`.
   - Ghi bản ghi `Transaction` loại `ESCROW_REFUND`.
   - Cập nhật `contract.paymentStatus = 'REFUNDED'`, `contract.status = 'CANCELLED'`.

### 3.5. Tầng `exception/` & Danh Mục Lỗi `PaymentErrorCode`
Triển khai interface `AppErrorCode`:

```java
public enum PaymentErrorCode implements AppErrorCode {
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_404_001", "Ví điện tử không tồn tại"),
    INSUFFICIENT_FUNDS(HttpStatus.BAD_REQUEST, "PAYMENT_400_001", "Số dư khả dụng không đủ để thực hiện giao dịch"),
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_404_002", "Hợp đồng không tồn tại"),
    INVALID_CONTRACT_STATE(HttpStatus.CONFLICT, "PAYMENT_409_001", "Trạng thái hợp đồng không hợp lệ cho thao tác này"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "PAYMENT_409_002", "Giao dịch đang được xử lý hoặc đã hoàn tất"),
    SIGNATURE_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_400_002", "Chữ ký xác thực cổng thanh toán không hợp lệ"),
    UNAUTHORIZED_WALLET_ACCESS(HttpStatus.FORBIDDEN, "PAYMENT_403_001", "Bạn không có quyền truy cập vào ví này");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    // Constructor, Getters...
}
```

### 3.6. Tầng `scheduler/` (Tác Vụ Ngầm Định Kỳ)
1. **`EscrowTimeoutScheduler`**: Quét các hợp đồng có milestone đã nộp sản phẩm nhưng Employer quá hạn nghiệm thu (ví dụ 14 ngày không phản hồi), tự động giải ngân cho Freelancer theo chính sách bảo vệ.
2. **`PaymentReconciliationScheduler`**: Định kỳ kiểm tra các giao dịch đang ở trạng thái `PENDING` quá 30 phút, gọi API truy vấn đối soát của cổng thanh toán (VNPAY QueryDR / Stripe Session API) để cập nhật trạng thái `SUCCESS` hoặc `FAILED`.

### 3.7. Bảo Mật Webhook Cổng Thanh Toán & Tính Idempotency
Đối với các endpoint nhận callback từ VNPAY (`/payments/vnpay/callback`) hoặc Stripe (`/payments/stripe/webhook`):
1. **Xác thực chữ ký số**: Dùng `PaymentSignatureVerifier` tính toán mã băm HMAC-SHA512 với `SecretKey` cấu hình trong `.env`, nếu sai lập tức trả về lỗi 400.
2. **Chống lặp giao dịch (Idempotency)**:
   - Kiểm tra `transaction_reference_id` từ cổng thanh toán.
   - Nếu đã tồn tại giao dịch thành công trong `transactions`, lập tức trả về `200 OK` (Idempotent response), không cộng tiền lặp lại.

---

## 4. Quản Lý Migration Schema CSDL (`occupify_payment`)

- Quản lý phiên bản CSDL bằng **Flyway**: `src/main/resources/db/migration/V1__init_payment_schema.sql`.
- Script tạo 4 bảng (`wallets`, `transactions`, `contracts`, `cancellation_contracts`) kèm theo các ràng buộc:
  - `CONSTRAINT chk_wallet_available_positive CHECK (available_balance >= 0)`
  - `CONSTRAINT chk_wallet_locked_positive CHECK (locked_balance >= 0)`
  - `CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0)`
  - Chỉ mục đánh trên các khóa ngoại logic (`idx_contracts_employer`, `idx_contracts_employee`, `idx_wallets_user_id`).
