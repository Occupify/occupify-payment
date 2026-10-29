# Occupify Domain Invariants & Business Rules

> **Phạm vi**: Toàn bộ hệ sinh thái Occupify, đặc biệt bắt buộc trong Bounded Context **Payment** (`occupify-payment`).

---

## 1. Mô Hình Người Dùng Hai Vai Trò (Dual-Role Invariant)

### 🚨 Nguyên tắc cốt lõi: EMPLOYEE và EMPLOYER là một người
Trong hệ sinh thái Occupify:
1. **Một tài khoản duy nhất (Single Identity)**:
   - Mỗi người dùng chỉ đăng ký một tài khoản duy nhất trong Bounded Context **Identity** (`users.id`).
   - Hệ thống **KHÔNG tách biệt** hai loại tài khoản riêng biệt cho "Freelancer" và "Khách hàng tuyển dụng".
2. **Giao diện hợp nhất với hai không gian làm việc (Unified UI with Dual Workspaces)**:
   - Cùng trên một giao diện web/mobile, người dùng có thể chuyển đổi hoặc truy cập đồng thời hai khu vực chức năng:
     - **Freelancer Workspace (Vai trò EMPLOYEE)**: Dành cho mục đích tìm việc, nộp hồ sơ báo giá (Bids), nhận lời mời (Offers), thực hiện công việc theo hợp đồng và nhận tiền thù lao giải ngân vào ví.
     - **Client Workspace (Vai trò EMPLOYER)**: Dành cho mục đích đăng tin dự án/công việc (Projects/Jobs), tìm kiếm chuyên gia, gửi lời mời (Offers), nạp tiền ký quỹ (Escrow) và nghiệm thu thanh toán cho freelancer.
3. **Ví điện tử hợp nhất (Unified Wallet)**:
   - Mỗi user chỉ sở hữu **duy nhất 1 ví tiền** (`wallets.user_id = users.id`).
   - Ví này đóng vai trò kép:
     - Tích lũy tiền thù lao kiếm được khi đóng vai trò Freelancer (`ESCROW_RELEASE` -> `available_balance`).
     - Dùng nguồn tiền sẵn có để nạp cọc ký quỹ khi đóng vai trò Client thuê người khác (`available_balance` -> `locked_balance` qua `ESCROW_HOLD`).
4. **Quy tắc cấm tự ký hợp đồng với chính mình (Prohibition of Self-Contracting)**:
   - Trong bảng `contracts`, luôn luôn bắt buộc:
     $$\text{employer\_id} \neq \text{employee\_id}$$
   - Một người dùng có thể là `employer` trong Hợp đồng #1 và đồng thời là `employee` trong Hợp đồng #2. Nhưng một người **tuyệt đối không thể tự thuê chính mình**.

---

## 2. Quy Tắc Bất Biến Tài Chính & Ký Quỹ (Financial & Escrow Invariants)

1. **Ràng buộc số dư không âm (Balance Non-Negativity)**:
   - `available_balance >= 0` VÀ `locked_balance >= 0` tại mọi thời điểm.
   - Bắt buộc kiểm tra ràng buộc ở cả 3 tầng: Database Check Constraint, JPA Entity Validation và Service Layer logic.
2. **Khóa Concurrency bắt buộc (Pessimistic Locking)**:
   - Mọi thao tác đọc ví để chuẩn bị cộng/trừ số dư (`ESCROW_HOLD`, `ESCROW_RELEASE`, `ESCROW_REFUND`, `DEPOSIT`, `WITHDRAWAL`) bắt buộc phải dùng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên `WalletRepository`.
   - Nghiêm cấm cập nhật số dư bằng cách đọc thường (read-only) rồi ghi đè bộ nhớ, để ngăn chặn race condition khi nhiều luồng thực hiện đồng thời.
3. **Bất biến sổ cái (Append-Only Ledger)**:
   - Bảng `transactions` là sổ cái bất biến.
   - Nghiêm cấm câu lệnh `UPDATE amount` hoặc `DELETE` trên các bản ghi giao dịch đã thành công (`SUCCESS`).
   - Mọi khoản hoàn tiền hoặc điều chỉnh đều phải tạo một bản ghi giao dịch mới đối ứng (`ESCROW_REFUND`, `COMPENSATION`).
4. **Idempotency cho Cổng Thanh Toán**:
   - Webhook callback từ cổng thanh toán bên ngoài (VNPAY, Stripe, MoMo) phải kiểm tra `transaction_reference_id`.
   - Nếu giao dịch đã ở trạng thái `SUCCESS`, trả về ngay lập tức mã `200 OK`, không lặp lại việc cộng tiền vào ví.
