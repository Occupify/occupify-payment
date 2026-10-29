# Occupify Payment Service — Agent Operational Guide

> **Service**: `occupify-payment`  
> **Bounded Context**: **Payment**  
> **Tech Stack**: Spring Boot 3.3.4, Java 25, Spring Data JPA, PostgreSQL 16 (Alpine), Flyway, MapStruct  
> **Architecture**: Microservice Architecture (Database-per-Service, Event-Driven, Saga/Outbox Pattern)  
> **Gateway Port**: `8180` | **Service Port**: `8183` | **Database**: `occupify_payment`

---

## 1. Product Context & Service Mission

### 1.1. What is Occupify?
*(Sourced from `.agent/OCCUPIFY.md`)*

**Occupify** is a next-generation freelance career platform — designed as **"LinkedIn tailored specifically for Freelancers"**.

Unlike traditional job boards where freelancers are treated as secondary or transactional candidates, Occupify places **Freelancers as first-class citizens** across three core pillars:
1. **Freelancer Career Hub** *(Identity Context)*: Deep profile customization showcasing verified skills, portfolio case studies, transparent hourly/fixed rates, and verified client ratings.
2. **Intelligent Job Marketplace** *(Core Context)*: Fast, filterable discovery matching freelance specialists with hiring clients, project managers, and agencies via structured bids and direct offers.
3. **Contract & Workspace Delivery with Financial Escrow** *(Payment Context — THIS SERVICE)*: Integrated workspace ensuring active project delivery, milestone tracking, and 100% guaranteed financial safety through automated escrow holding.

### 1.2. The Mission of the Payment Bounded Context
Freelancers often struggle with payment defaults, delayed disbursements, and opaque financial disputes on generic platforms. `occupify-payment` solves these fundamental trust issues:
- **Escrow-Backed Contracts**: Client funds are verified and locked (`locked_balance`) before work begins (`IN_PROGRESS`), ensuring freelancers can deliver with full confidence of payment.
- **Flexible Compensation Models**: Native support for diverse freelance billing preferences: `FIXED`, `HOURLY`, `DAILY`, `WEEKLY`, and `MONTHLY` (`salary_type`).
- **Fair Dispute & Cancellation Flow**: Standardized, evidence-based contract termination handling (`cancellation_contracts`) protecting both freelancer effort and client investment.
- **Dual-Bucket Digital Wallets**: Real-time balance separation between spendable funds (`available_balance`) and escrowed funds (`locked_balance`), with multi-channel top-ups and payouts (Bank Transfer, VNPAY, Stripe, MoMo).
- **Append-Only Ledger**: Full financial traceability via immutable records in `transactions`.

### 1.3. Critical Domain Invariant: Dual-Role User Model (EMPLOYEE và EMPLOYER là một người)
Trong hệ thống Occupify, **EMPLOYEE và EMPLOYER là cùng một người**:
- **Tài khoản duy nhất (Single Identity)**: Người dùng chỉ đăng ký một tài khoản (`users.id`). Hệ thống không tách biệt tài khoản riêng cho Freelancer và Client.
- **Giao diện hợp nhất 2 không gian làm việc (Unified UI with Dual Workspaces)**: Cùng một giao diện web/mobile cung cấp 2 khu vực phục vụ 2 mục đích của 2 vai trò:
  - *Freelancer Workspace (Vai trò EMPLOYEE)*: Dành cho mục đích tìm việc, nộp hồ sơ báo giá (Bids), nhận lời mời (Offers), thực hiện hợp đồng và nhận tiền giải ngân vào ví.
  - *Client Workspace (Vai trò EMPLOYER)*: Dành cho mục đích đăng tin dự án/công việc (Projects/Jobs), tìm kiếm ứng viên, gửi lời mời (Offers), nạp tiền ký quỹ (Escrow) và trả thù lao cho freelancer khác.
- **Ví điện tử hợp nhất (Unified Wallet)**: Mỗi user chỉ có **duy nhất 1 ví tiền** (`wallets.user_id = users.id`). Ví này vừa tích lũy thu nhập từ việc làm freelancer, vừa dùng để thanh toán ký quỹ khi thuê freelancer khác.
- **Ràng buộc cấm tự ký hợp đồng với chính mình**: Trong mọi hợp đồng (`contracts`), luôn đảm bảo `employer_id != employee_id`. Người dùng có thể là employer trong Hợp đồng A và employee trong Hợp đồng B.

### 1.4. Key Stakeholder Personas & Administrative Roles
- **Người dùng cá nhân (Dual-Role User)**: Đóng vai trò kép vừa là Freelancer vừa là Client trên cùng một tài khoản và ví tiền.
- **Platform Administrator (`ADMIN`)**: Quản trị viên hệ thống, hòa giải khiếu nại tranh chấp hợp đồng (`DISPUTED`), kiểm soát rủi ro và đối soát dòng tiền.
---
## 2. System Bounded Contexts & Microservice Boundaries

The Occupify ecosystem is cleanly decomposed into **5 Bounded Contexts**:

```
+-----------------------------------------------------------------------------------------------------------------------+
|                                                  OCCUPIFY ECOSYSTEM                                                   |
+-------------------+-------------------+------------------------------+-----------------------+------------------------+
|  Identity         |  Core             |  Payment (THIS SERVICE)      |  Notification         |  Administration        |
+-------------------+-------------------+------------------------------+-----------------------+------------------------+
|  occupify-identity|  occupify-core    |  occupify-payment            |  occupify-notification|  occupify-admin        |
|  Port: 8181       |  Port: 8182       |  Port: 8183                  |  Port: 8184           |  Port: 8185            |
|                   |                   |                              |                       |                        |
|  - Users & Auth   |  - Projects & Jobs|  - Wallets (Available/Locked)|  - In-App Alerts      |  - Platform Moderation |
|  - User Profiles  |  - Bids & Offers  |  - Transactions (Ledger)     |  - Event Notices      |  - Reports & Evidences |
|  - Experiences    |  - Reviews        |  - Contracts & Terms         |  - User Notifications |  - Flagged Accounts    |
|  - Educations     |  - Skills Catalog |  - Cancellations & Disputes  |                       |  - System Audit Logs   |
|  - Qualifications |                   |                              |                       |                        |
+-------------------+-------------------+------------------------------+-----------------------+------------------------+
```

### 🚨 Strict Microservice Guardrails

1. **Database-per-Service Isolation**:
   - `occupify-payment` exclusively connects to its own database (`occupify_payment`).
   - **NEVER** write queries targeting tables belonging to other bounded contexts.
   - **NEVER** define JPA `@ManyToOne` / `@OneToMany` pointing to external entities like `User` or `Job`.
2. **Logical Foreign Keys (`UUID`)**:
   - References to external domains are stored strictly as raw `UUID` identifiers (e.g., `UUID employer_id`, `UUID employee_id`, `UUID project_role_id`, `UUID source_id`).
3. **API Gateway Integration (`occupify-gateway` on Port 8180)**:
   - Inbound routes matching `/contracts/**`, `/wallets/**`, `/payments/**` are routed to `occupify-payment` (`:8183`).
   - The Gateway's `AuthenticationGatewayFilter` validates the JWT Bearer token, strips client-spoofed headers on public routes, and injects verified authentication headers downstream:
     - `X-User-Id`: Authenticated User UUID
     - `X-User-Email`: Authenticated User Email
     - `X-User-Role`: User Role (`FREELANCER`, `CLIENT`, `ADMIN`)
     - `X-Correlation-Id`: Distributed tracing correlation UUID (via `CorrelationIdFilter`)
   - `occupify-payment` extracts these headers via `UserContextFilter` into `UserContextHolder` for thread-local access, avoiding redundant cryptographic JWT parsing.
   - The Gateway applies `paymentCircuitBreaker` (Resilience4j) with fallback to `forward:/fallback/payment` and `Retry` (3 attempts on GET/HEAD for 502/503/504).
4. **Financial Invariants (Balance & Escrow Safety)**:
   - **Non-negative balances**: `available_balance >= 0` and `locked_balance >= 0` at all times.
   - **Pessimistic Concurrency**: Any balance mutation MUST acquire a pessimistic write lock via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `WalletRepository`.
   - **Ledger Immutability**: The `transactions` table is append-only. Never perform SQL `UPDATE` or `DELETE` on finalized transaction records.
5. **Asynchronous Consistency (Saga Pattern)**:
   - State synchronization across Bounded Contexts (e.g., Core, Notification, Administration) is mediated via Event-Driven Architecture (Kafka / RabbitMQ) using Domain Events (`ContractActivatedEvent`, `EscrowLockedEvent`, `PaymentReleasedEvent`).
   - Webhook integrations (e.g., VNPAY IPN, Stripe Webhooks) MUST enforce **Idempotency** via unique transaction/gateway reference IDs.

---

## 3. Directory Layout & Architecture Map

The project follows a clean, technically layered Spring Boot architecture under `com.occupify.payment`:

```
occupify-payment/
├── .agent/
│   ├── ARCHITECTURE.md             # Service-specific architectural blueprint for occupify-payment
│   ├── ECOSYSTEM-ARCHITECTURE.md   # Shared microservices architecture blueprint across all Occupify services
│   ├── OCCUPIFY.md                 # Product vision, freelancer-first domain briefing
│   ├── README.md                   # Agent system setup & personas guide
│   ├── rules/                      # Domain invariants (Dual-role, Escrow) and verification protocols
│   └── agents/                     # Spring Boot subagent personas (tech-lead, core-dev, gateway-dev, etc.)
├── docs/
│   ├── ECOSYSTEM-ERD.md            # Shared 5-service ERD & schema catalog across all Occupify services
│   ├── ERD.md                      # Service-specific ERD for occupify-payment (4 tables + financial invariants)
│   ├── IDENTITY-ERD.md             # Service-specific ERD for occupify-identity (Users, Profiles, Skills)
│   ├── CORE-ERD.md                 # Service-specific ERD for occupify-core (Projects, Jobs, Bids, Offers, Reviews)
│   ├── NOTIFICATION-ERD.md         # Service-specific ERD for occupify-notification (In-app notifications)
│   ├── ADMINISTRATION-ERD.md       # Service-specific ERD for occupify-admin (Reports, Flagged accounts, Audit logs)
│   └── Response-ErrorHandling-Architecture.md # Project response & exception envelope specifications
├── src/
│   ├── main/
│   │   ├── java/com/occupify/payment/
│   │   │   ├── config/             # Spring @Configuration (Security, CORS, OpenAPI, Jackson)
│   │   │   ├── constant/           # Route paths, error codes, event topics, regex constants
│   │   │   ├── controller/         # Thin REST Controllers (extends AbstractBaseController)
│   │   │   ├── dto/
│   │   │   │   ├── base/           # Base responses 
│   │   │   │   ├── request/        # Inbound request records with Jakarta Validation (@Valid)
│   │   │   │   └── response/       # Outbound response records (AbstractBaseResponse, SingleResponse, PageResponse)
│   │   │   ├── entity/             # JPA Entities: Contract, CancellationContract, Wallet, Transaction
│   │   │   ├── enums/              # Domain enums: ContractStatus, PaymentStatus, TransactionType, etc.
│   │   │   ├── event/              # Domain event schemas, publishers, and consumer listeners (Saga)
│   │   │   ├── exception/          # Custom BaseAppException hierarchy & AppExceptionHandler
│   │   │   ├── mapper/             # MapStruct component mappers (Entity <-> DTO)
│   │   │   ├── repository/         # Spring Data JPA repositories with locking and custom queries
│   │   │   ├── scheduler/          # Background cron jobs (escrow timeout, payment reconciliation)
│   │   │   ├── security/           # Gateway header extraction (X-User-Id), UserContextHolder
│   │   │   ├── service/            # Core business interfaces and @Transactional implementations
│   │   │   │   └── impl/
│   │   │   ├── util/               # Stateless utilities (money math, HMAC signature verification)
│   │   │   └── OccupifyPaymentApplication.java
│   │   └── resources/
│   │       ├── application.yml     # Base configuration (port 8183, PostgreSQL URL)
│   │       └── db/migration/       # Flyway SQL migration scripts (V1__init_payment_schema.sql)
│   └── test/                       # Unit tests, slice tests (@DataJpaTest, @WebMvcTest), integration tests
├── docker-compose.yml              # Local PostgreSQL 16 container definition
├── Dockerfile                      # Production container build
├── Makefile                        # Unified lifecycle runner (Docker & Podman auto-detected)
├── pom.xml                         # Maven dependencies & build plugins
├── AGENT.md                        # This operational briefing
└── README.md                       # Project quickstart
```

For ecosystem-wide standards, consult **[.agent/ECOSYSTEM-ARCHITECTURE.md](.agent/ECOSYSTEM-ARCHITECTURE.md)** and **[docs/ECOSYSTEM-ERD.md](docs/ECOSYSTEM-ERD.md)**.  
For Payment service-specific technical rules, consult **[.agent/ARCHITECTURE.md](.agent/ARCHITECTURE.md)** and **[docs/ERD.md](docs/ERD.md)**.

---

## 4. Development Workflow & Commands

The project uses a cross-compatible `Makefile` supporting both **Docker** and **Podman**:

```bash
# 1. Start local infrastructure (PostgreSQL 16 on port 5532)
make infra

# 2. Run Spring Boot application locally (dev profile, port 8183)
make up

# 3. Stop local infrastructure
make down

# 4. View help & active container engine
make help

# 5. Run Maven test suite
./mvnw test

# 6. Build production JAR
./mvnw clean package -DskipTests
```

---

## 5. Coding Standards Checklist for Agents

Before completing any task, ensure:
- [ ] **Envelope Pattern Compliance**: Controllers inherit `AbstractBaseController` and return standardized responses using `ResponseFactory` (`SingleResponse<T>`, `SuccessResponse`, `SingleResponse<PageResponse<T>>`).
- [ ] **Input Validation**: All request DTO fields annotated with Jakarta Validation (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`).
- [ ] **Locking on Balance Updates**: `WalletRepository` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` for all balance-modifying operations.
- [ ] **Transactional Boundaries**: Methods performing database mutations annotated with `@Transactional`. Read-only queries use `@Transactional(readOnly = true)`.
- [ ] **No Cross-Service Direct Coupling**: No references to other bounded context databases, foreign keys, or synchronous blocking calls inside financial transaction blocks.
- [ ] **Gateway Identity Propagation**: Retrieve authenticated identity via `UserContextHolder.getUserId()` (populated from `X-User-Id`), never trust unverified client-supplied user IDs in request bodies.
- [ ] **Clean Errors**: Exceptions extend `BaseAppException` with domain-specific `AppErrorCode` (e.g., `PaymentErrorCode`), handled by `AppExceptionHandler` returning `ErrorResponse` or `DetailedErrorResponse`.
- [ ] **Flyway Versioning**: All schema modifications are versioned in `src/main/resources/db/migration/V{N}__{description}.sql`.
