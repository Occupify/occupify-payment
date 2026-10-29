# Occupify Payment Service — Agent Customization System

Thư mục `.agent/` chứa cấu hình, quy tắc kiến trúc (rules), tài liệu tham chiếu (blueprints) và các vai trò AI subagents chuyên biệt cho dịch vụ **Payment & Contract Service** (`occupify-payment`) trong hệ sinh thái Occupify.

---

## 1. Cấu Trúc Thư Mục `.agent/` Sau Khi Chuẩn Hóa

```
.agent/
├── ARCHITECTURE.md             # Kiến trúc chuyên biệt cho Payment Service (Port 8183, 4 bảng, Escrow, Webhook)
├── ECOSYSTEM-ARCHITECTURE.md   # Chuẩn kiến trúc Microservices toàn hệ sinh thái (Gateway, Envelopes, Saga, Flyway)
├── README.md                   # Tài liệu hướng dẫn hệ thống agent (FILE NÀY)
│
├── rules/                      # Quy tắc bất biến bắt buộc tuân thủ
│   ├── domain-invariants.md    # Mô hình Người dùng 2 vai trò (EMPLOYEE & EMPLOYER là một người), Ví duy nhất, Invariant Escrow
│   └── verification.md         # Quy tắc kiểm tra tính toàn vẹn đường dẫn và xác minh trước khi trả lời
│
└── agents/                     # Danh sách các AI Subagent Personas chuyên biệt cho Backend
    ├── spring-tech-lead.md     # Backend Architect & Squad Orchestrator
    ├── spring-core-dev.md      # Core Domain Developer: Entities, Balance Logic, Pessimistic Locking
    ├── api-gateway-dev.md      # REST Controller & Gateway Integration Developer
    ├── backend-qa-tester.md    # TDD & Slice Testing Specialist (@WebMvcTest, @DataJpaTest)
    └── backend-quality-auditor.md # ResponseFactory & Security Gatekeeper
```

---

## 2. Danh Sách Personas (Subagents)

Bạn có thể gọi trực tiếp hoặc chỉ định các persona này trong IDE chat:

| Subagent Persona | Vai trò & Trách nhiệm chính |
|:---|:---|
| **`@spring-tech-lead`** | **Kiến trúc sư trưởng backend & Điều phối squad**: Phân tích ranh giới bounded context, quy chuẩn API OpenAPI, giám sát migration Flyway tuần tự và điều phối quy trình TDD. |
| **`@spring-core-dev`** | **Kỹ sư nghiệp vụ cốt lõi**: Hiện thực hóa Entities JPA, Spring Data JPA Repositories (với `@Lock(LockModeType.PESSIMISTIC_WRITE)`), tính toán biến động số dư ví, logic Ký quỹ Escrow và các event producer/consumer Saga. |
| **`@api-gateway-dev`** | **Kỹ sư tích hợp Gateway & Controller**: Hiện thực REST Controllers kế thừa `AbstractBaseController`, đóng gói response qua `ResponseFactory`, viết `UserContextFilter` đọc header `X-User-*` từ Gateway. |
| **`@backend-qa-tester`** | **Chuyên gia kiểm thử TDD**: Viết failing test trước theo phong cách TDD, thực hiện Slice Test (`@WebMvcTest` cho controller, `@DataJpaTest` cho repository) và Mockito unit tests cho service. |
| **`@backend-quality-auditor`** | **Kiểm toán viên chất lượng & bảo mật**: Rà soát tuân thủ Envelope Pattern, chuẩn mã lỗi `PaymentErrorCode`, bảo mật chống race condition số dư và quy tắc zero-failure build (`./mvnw clean test`). |

---

## 3. Quy Tắc Bất Biến Cần Nắm (Rules)

1. **`domain-invariants.md`**:
   - **EMPLOYEE và EMPLOYER là một người**: Mỗi user chỉ có 1 tài khoản và 1 ví điện tử duy nhất. Cùng trên một giao diện có 2 workspace (Freelancer & Client). Cấm tự ký hợp đồng với chính mình (`employer_id != employee_id`).
   - **An toàn số dư**: `available_balance >= 0` và `locked_balance >= 0`. Mọi biến động số dư bắt buộc dùng Pessimistic Write Lock. Sổ cái `transactions` là bất biến (append-only).
2. **`verification.md`**:
   - Xác minh vị trí file và đường dẫn tương đối thực tế trong workspace trước khi tham chiếu. Không phỏng đoán.

---

## 4. Tài Liệu Kiến Trúc Tham Chiếu

- **Dùng chung toàn hệ sinh thái**: [ECOSYSTEM-ARCHITECTURE.md](ECOSYSTEM-ARCHITECTURE.md) (kèm [docs/ECOSYSTEM-ERD.md](../docs/ECOSYSTEM-ERD.md)). Copy 2 file này sang các microservice khác khi khởi tạo.
- **Dành riêng cho Payment Service**: [ARCHITECTURE.md](ARCHITECTURE.md) (kèm [docs/ERD.md](../docs/ERD.md)).
- **Quy chuẩn Response & Exception**: [docs/Response-ErrorHandling-Architecture.md](../docs/Response-ErrorHandling-Architecture.md).
