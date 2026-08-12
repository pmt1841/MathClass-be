# Specification: AI Credit System — Hạn mức Credit theo từng Người dùng (`MathClass-service` & `MathClass-fe`)

---

## 1. Feature Overview
- **Feature Name:** AI Credit System — Cấp credit mặc định theo role, trừ credit mỗi lần gọi AI, mua thêm khi hết
- **Jira Ticket:** [MAT-255](https://phanvanluan611996.atlassian.net/browse/MAT-255)
- **Target Subsystems:** `MathClass-service` (Backend), `MathClass-fe` (Next.js Frontend)
- **Target Users:** System Administrator, Teacher, Student

---

## 2. Business Goal & Core Objectives

Thay thế mô hình "daily limit theo vai trò" bằng **hệ thống Credit cá nhân hóa**:

1. Mỗi người dùng có **tài khoản credit riêng** (`balance`).
2. Admin cấu hình **credit mặc định** cấp cho mỗi người dùng mới theo vai trò (STUDENT / TEACHER).
3. Mỗi lần gọi AI tiêu tốn credit **tính theo số token đầu ra (completion tokens)** của phản hồi, theo tỷ lệ `tokens_per_credit` do admin cấu hình, kèm phí tối thiểu `cost_per_call`.
4. Khi hết credit → **mua thêm** qua gói credit (`CreditPackage`).
5. Thanh toán: **nạp mô phỏng (Mock)** trước, nhưng phải tách qua interface `PaymentGateway` để sau này thay cổng thật (VNPay/MoMo/Stripe) mà không sửa logic nghiệp vụ.

Bảng ánh xạ task → chi phí gợi ý (admin cấu hình):

| task_code | Tính năng | cost_per_call (tối thiểu) | tokens_per_credit (gợi ý) |
| :--- | :--- | :--- | :--- |
| `STUDENT_HINT` | Gợi ý tư duy | 1 | 1000 |
| `CANVAS_LATEX` | Chuyển ảnh chữ viết tay → LaTeX | 2 | 1000 |
| `QUESTION_GEN` | Sinh đề | 3 | 1000 |
| `SUBMISSION_GRADING` | Chấm bài tự động | 5 | 1000 |

---

## 3. Potential Logic Loopholes & Mitigations (5 Key Edge Cases)

### 3.1. Case 1: Double-Spend — 2 request AI song song cùng trừ credit
- **Vấn đề:** User còn 1 credit, gọi 2 lần cùng lúc. Cả 2 đọc `balance=1` → cùng trừ → số dư âm, hệ thống lỗ.
- **Khắc phục:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên dòng `user_ai_accounts` trong toàn bộ reserve; kiểm tra `balance >= cost` ngay trong transaction.

### 3.2. Case 2: Phạt trừ credit khi AI lỗi (System Fault Penalty)
- **Vấn đề:** Trừ credit trước khi gọi AI; AI lỗi (429/500/timeout) → user mất credit vô ích.
- **Khắc phục:** Mô hình **Reserve-then-Refund**:
  1. **Reserve:** Lock + trừ `cost_per_call`, ghi `CONSUME` transaction (đặt chỗ).
  2. Gọi AI.
  3. **Thành công:** giữ nguyên.
  4. **Thất bại:** `refund()` — cộng lại `cost_per_call` + ghi `REFUND` transaction.

### 3.3. Case 3: Nạp credit trùng lặp khi user gửi nhiều lần confirm
- **Vấn đề:** User/script gọi `POST /purchase/{orderId}/complete` nhiều lần → credit bị cộng nhiều lần cho 1 đơn.
- **Khắc phục:** Chuyển trạng thái đơn `PENDING → SUCCESS` là **idempotent**: chỉ cộng credit khi đơn đang `PENDING` (so sánh + lock), mọi lần gọi sau trả về trạng thái hiện tại mà không cộng thêm.

### 3.4. Case 4: User cũ chưa có tài khoản credit sau khi deploy
- **Vấn đề:** Tính năng mới deploy, các user đã tồn tại không có `user_ai_accounts` → mọi lần gọi AI lỗi "không tìm thấy tài khoản".
- **Khắc phục:** Seeder **backfill**: với mỗi user chưa có account, cấp `default_credits` theo role. Đồng thời `AiCreditService.getOrCreateAccount(userId)` tạo lazy khi thiếu (phòng user mới tạo giữa 2 lần chạy seeder).

### 3.5. Case 5: Nhầm lẫn lỗi "hết credit" với lỗi khác trên FE
- **Vấn đề:** FE cần hiển thị CTA "Mua thêm credit" đúng lúc, không nhầm với lỗi hệ thống.
- **Khắc phục:** Dùng HTTP `402 Payment Required` + `errorCode = "INSUFFICIENT_CREDITS"` + message thân thiện *"Bạn đã hết credit AI. Vui lòng mua thêm."* — khác biệt rõ với `429` (rate-limit) và `500`.

### 3.6. Case 6: Provider không trả thông tin usage (token)
- **Vấn đề:** Một số model/proxy không trả `usage.completion_tokens` (OpenAI) hoặc `usageMetadata.candidatesTokenCount` (Gemini) → không thể tính credit theo token.
- **Khắc phục:** Strategy trả `completionTokens = null`; `computeCredits()` fallback về **phí tối thiểu `cost_per_call`** — không phạt người dùng, không miễn phí.

---

## 4. Functional Requirements

- **FR-1 (User AI Account):** Mỗi user có tài khoản credit (`balance`, `total_earned`, `total_spent`).
- **FR-2 (Default Credit Grant):** Admin cấu hình `default_credits` theo role; tự cấp khi user đăng ký; backfill user cũ khi deploy.
- **FR-3 (Credit Cost Config):** Admin cấu hình `cost_per_call` (phí tối thiểu mỗi lượt) và `tokens_per_credit` (số token đầu ra = 1 credit) cho từng task AI.
- **FR-4 (Enforcement at AI Gateway):** Mọi gọi AI qua `AiPromptExecutionService` đều kiểm tra & trừ credit (reserve/refund).
- **FR-5 (Credit Ledger):** Mọi biến động số dư ghi vào `credit_transactions` (GRANT_DEFAULT / PURCHASE / ADMIN_ADJUST / CONSUME / REFUND) — truy vết đầy đủ.
- **FR-6 (Credit Packages & Mock Purchase):** Admin quản lý gói credit; user chọn gói → tạo đơn → `PaymentGateway` xử lý (Mock = thành công ngay).
- **FR-7 (Balance & History API):** User xem số dư, lịch sử giao dịch, bảng giá chi phí từng task.
- **FR-8 (Insufficient Credit Feedback):** Hết credit → `402` + errorCode `INSUFFICIENT_CREDITS` + thông báo hướng dẫn mua thêm.

---

## 5. Business Rules

- **BR-1 (Non-Negative Balance):** Số dư không bao giờ âm; reserve phải trong transaction có lock.
- **BR-2 (Non-Penalty Fault Rule):** AI lỗi → hoàn lại credit (REFUND), không tính phí.
- **BR-3 (Idempotent Purchase):** Mỗi đơn chỉ cộng credit đúng 1 lần (PENDING → SUCCESS).
- **BR-4 (Admin Not Charged):** `ROLE_ADMIN` không bị trừ credit khi gọi AI (hoặc cấu hình `enabled=false`).
- **BR-5 (Existing Per-Assignment Limit):** Giữ nguyên `MAX_HINTS = 3`/bài tập — khác tầng với credit.
- **BR-6 (Ledger Integrity):** Không sửa/xóa transaction đã ghi; mọi điều chỉnh sai lệch qua `ADMIN_ADJUST` mới.
- **BR-7 (No Sensitive Data):** Không log balance/payment chi tiết dạng nhạy cảm ngoài mức cần thiết.
- **BR-8 (Token-Based Charging):**
  - `estimate = max(cost_per_call, ceil(max_token / tokens_per_credit))` — đặt chỗ trước khi gọi AI (trần theo `maxToken` của task).
  - `actual = max(cost_per_call, ceil(completion_tokens / tokens_per_credit))` — trừ theo token đầu ra thực tế.
  - Nếu `tokens_per_credit` = NULL/0 hoặc provider KHÔNG trả `completion_tokens` → `actual = cost_per_call` (fallback an toàn).
- **BR-9 (Settle-only-refund):** Sau khi AI thành công, chỉ hoàn phần dư `estimate - actual` (REFUND). Không trừ thêm khi `actual > estimate` (token không thể vượt trần `maxToken`).

---

## 6. Data Model

> `spring.jpa.hibernate.ddl-auto=update` → entity mới tự tạo bảng. Kế thừa `BaseEntity` (`id`, `created_at`, `updated_at`).

### 6.1. `UserAiAccount` — bảng `user_ai_accounts`
- **Java Class:** `com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount extends BaseEntity`

```sql
CREATE TABLE user_ai_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    balance INT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_earned INT NOT NULL DEFAULT 0,
    total_spent INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 6.2. `AiCreditDefault` — bảng `ai_credit_defaults`
- **Java Class:** `com.codegym.mathclass.aiconfig.credit.entity.AiCreditDefault extends BaseEntity`

```sql
CREATE TABLE ai_credit_defaults (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(20) NOT NULL UNIQUE,        -- STUDENT | TEACHER
    default_credits INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 6.3. `AiCreditConfig` — bảng `ai_credit_configs` (chi phí theo task)
- **Java Class:** `com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig extends BaseEntity`

```sql
CREATE TABLE ai_credit_configs (
    id BIGSERIAL PRIMARY KEY,
    task VARCHAR(50) NOT NULL UNIQUE,        -- STUDENT_HINT | SUBMISSION_GRADING | ...
    cost_per_call INT NOT NULL DEFAULT 1 CHECK (cost_per_call >= 0),   -- phí tối thiểu mỗi lượt
    tokens_per_credit INT NULL,              -- số token đầu ra = 1 credit (NULL/0 => dùng cost_per_call)
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 6.4. `CreditTransaction` — bảng `credit_transactions` (sổ cái)
- **Java Class:** `com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction extends BaseEntity`

```sql
CREATE TABLE credit_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount INT NOT NULL,                     -- (+) nạp/cấp/hoàn | (-) tiêu
    type VARCHAR(20) NOT NULL,               -- GRANT_DEFAULT | PURCHASE | ADMIN_ADJUST | CONSUME | REFUND
    task VARCHAR(50),                        -- chỉ khi type = CONSUME/REFUND
    reference_id BIGINT,                     -- purchase_order_id / ai_usage_id
    description VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_credit_txn_user ON credit_transactions(user_id, created_at DESC);
CREATE INDEX idx_credit_txn_type ON credit_transactions(type);
```

### 6.5. `CreditPackage` — bảng `credit_packages`
- **Java Class:** `com.codegym.mathclass.aiconfig.credit.entity.CreditPackage extends BaseEntity`

```sql
CREATE TABLE credit_packages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    credits INT NOT NULL CHECK (credits > 0),
    price INT NOT NULL CHECK (price > 0),    -- đơn vị VND
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 6.6. `CreditPurchaseOrder` — bảng `credit_purchase_orders`
- **Java Class:** `com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder extends BaseEntity`

```sql
CREATE TABLE credit_purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    package_id BIGINT NOT NULL REFERENCES credit_packages(id),
    credits INT NOT NULL,
    price INT NOT NULL,                      -- VND (snapshot từ package)
    gateway_code VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | SUCCESS | FAILED | CANCELLED
    transaction_ref VARCHAR(100),            -- mã giao dịch bên gateway
    paid_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_purchase_user ON credit_purchase_orders(user_id, created_at DESC);
```

### 6.7. Data Seed (`DatabaseSeeder`)

| Bảng | Seed mặc định |
| :--- | :--- |
| `ai_credit_defaults` | STUDENT = 100, TEACHER = 500 |
| `ai_credit_configs` | STUDENT_HINT = 1, CANVAS_LATEX = 2, QUESTION_GEN = 3, SUBMISSION_GRADING = 5 — kèm `tokens_per_credit` = 1000 (mọi task) |
| `credit_packages` | Gói Cơ bản (100 credit – 20.000đ), Gói Pro (300 credit – 50.000đ), Gói VIP (1.000 credit – 150.000đ) |
| Backfill | User cũ chưa có `user_ai_accounts` → cấp `default_credits` theo role + ghi `GRANT_DEFAULT` |

---

## 7. API Contract

> Prefix: `/api/v1`. Error chuẩn: `{ "message": "...", "errorCode": "..." }`.

### 7.1. User — Số dư & bảng giá
- **Endpoint:** `GET /api/v1/credits/me`
- **Authorization:** `isAuthenticated()`
- **Response `200 OK`:**
```json
{
  "code": 200,
  "data": {
    "userId": 42,
    "balance": 97,
    "totalEarned": 100,
    "totalSpent": 3,
    "costs": [
      { "task": "STUDENT_HINT", "costPerCall": 1, "tokensPerCredit": 1000 },
      { "task": "SUBMISSION_GRADING", "costPerCall": 5, "tokensPerCredit": 1000 }
    ]
  }
}
```

### 7.2. User — Danh sách gói mua
- **Endpoint:** `GET /api/v1/credits/packages`
```json
{
  "code": 200,
  "data": [
    { "id": 1, "name": "Gói Cơ bản", "credits": 100, "price": 20000 },
    { "id": 2, "name": "Gói Pro", "credits": 300, "price": 50000 }
  ]
}
```

### 7.3. User — Mua gói (tạo đơn + khởi tạo thanh toán)
- **Endpoint:** `POST /api/v1/credits/purchase`
- **Request:** `{ "packageId": 1 }`
- **Response `200 OK`:** `{ "orderId": 501, "gatewayCode": "MOCK", "redirectUrl": null, "status": "PENDING" }`

### 7.4. User — Xác nhận thanh toán (Mock: thành công ngay)
- **Endpoint:** `POST /api/v1/credits/purchase/{orderId}/complete`
- **Response `200 OK`:**
```json
{
  "code": 200,
  "data": { "orderId": 501, "status": "SUCCESS", "creditsAdded": 100, "newBalance": 197 }
}
```
- **Idempotent:** gọi lại lần 2 vẫn trả `SUCCESS`, **không** cộng thêm credit.

### 7.5. Admin — Chi phí theo task
- **Endpoint:** `PUT /api/v1/admin/ai-credit-config/tasks/{task}` — `@PreAuthorize("hasRole('ADMIN')")`
- **Request:** `{ "costPerCall": 2, "tokensPerCredit": 1000, "enabled": true }`
  - `costPerCall`: phí tối thiểu mỗi lượt.
  - `tokensPerCredit`: số token đầu ra = 1 credit; `null` giữ nguyên, `0` tắt tính theo token (fallback phí cố định).

### 7.6. Admin — Credit mặc định theo role
- **Endpoint:** `PUT /api/v1/admin/ai-credit-config/defaults/{role}` — `{ "defaultCredits": 200 }`

### 7.7. Admin — Điều chỉnh credit thủ công (grant / hoàn tiền)
- **Endpoint:** `POST /api/v1/admin/credits/adjust`
- **Request:** `{ "userId": 42, "amount": 500, "reason": "Hoàn tiền lỗi hệ thống" }` → ghi `ADMIN_ADJUST`, ghi `system_logs`.

### 7.8. Admin — Sổ cái giao dịch
- **Endpoint:** `GET /api/v1/admin/credits/transactions?userId=42&type=CONSUME&page=0&size=20`

### 7.9. Admin — CRUD gói credit
- **Endpoints:** `GET/POST /api/v1/admin/credit-packages`, `PUT/DELETE /api/v1/admin/credit-packages/{id}`

### 7.10. Error — Hết credit
- **HTTP Status:** `402 Payment Required`
```json
{ "message": "Bạn đã hết credit AI. Vui lòng mua thêm.", "errorCode": "INSUFFICIENT_CREDITS" }
```
- Triển khai: `InsufficientCreditException` + handler trong `GlobalExceptionHandler`.

---

## 8. Credit Enforcement Flow

Điểm chèn tập trung: **`AiPromptExecutionService`** — thêm overload (giữ API cũ):

```java
String executePrompt(String taskCode, String prompt);              // không đổi
String executePrompt(String taskCode, String prompt, Long userId); // mới: trừ credit
```

Quy trình:
1. Kiểm tra `TaskConfig` tồn tại + enabled (logic MAT-253 giữ nguyên).
2. Lấy `AiCreditConfig(task)`. **Không tồn tại / `enabled=false` / role ADMIN** → bỏ qua tính phí.
3. `estimate = max(cost_per_call, ceil(maxToken / tokens_per_credit))` → `creditService.reserve(userId, task, estimate)`: lock `user_ai_accounts`, nếu `balance < estimate` → ném `InsufficientCreditException` (402).
4. Gọi AI Provider → nhận `AiExecutionResult(content, completionTokens)`.
5. **Thành công:** `actual = max(cost_per_call, ceil(completionTokens / tokens_per_credit))` → `creditService.settle(userId, task, estimate, actual)` — hoàn phần dư `estimate - actual` (REFUND).
6. **Thất bại:** `creditService.refund(userId, task, estimate)` (hoàn toàn bộ, Non-Penalty).

Cập nhật consumer truyền `userId`:
- `SubmissionHintServiceImpl.requestHint(...)` → `student.getId()` (`STUDENT_HINT`).
- `AiGradingServiceImpl.requestAiGrading(...)` → `teacherId` (`SUBMISSION_GRADING`).

---

## 9. Payment Gateway Abstraction

### 9.1. Interface
```java
public interface PaymentGateway {
    String getCode();                                  // "MOCK" | "VNPAY" | "MOMO" ...
    PaymentInitResult initiate(CreditPurchaseOrder order);  // tạo phiên/URL thanh toán
    PaymentVerifyResult verify(CreditPurchaseOrder order);  // xác nhận kết quả
}
```

### 9.2. Implementations
- **`MockPaymentGateway`** (MVP): `initiate` trả về `PENDING`; `verify` luôn trả `SUCCESS` (hoặc giả lập FAILED qua cấu hình test).
- **Tương lai:** `VnpayPaymentGateway`, `MomopayPaymentGateway`... đăng ký qua `PaymentGatewayFactory` (`Map<String, PaymentGateway>`), **không sửa** `CreditPurchaseService`.

### 9.3. Luồng mua credit (mock)
1. `POST /credits/purchase {packageId}` → tạo `CreditPurchaseOrder(PENDING)` → `gateway.initiate(order)`.
2. `POST /credits/purchase/{orderId}/complete` → `gateway.verify(order)`.
3. `SUCCESS` → trong transaction: lock đơn, nếu `status == PENDING` thì set `SUCCESS`, cộng balance + `total_earned`, ghi `PURCHASE` transaction. (Idempotent — BR-3.)

---

## 10. Non-Functional Requirements & Implementation Constraints

- **Framework:** Java 21 LTS, Spring Boot 4.x, Spring Data JPA (giữ nguyên).
- **Database:** PostgreSQL 16 (`ddl-auto=update`); `CHECK (balance >= 0)` ở DB như lớp phòng vệ cuối.
- **Caching:** Caffeine — cache `ai_credit_configs_cache`, `ai_credit_defaults_cache`, `credit_packages_cache`; **không** cache balance (phải nhất quán).
- **Concurrency:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` cho balance & purchase order; tất cả trong `@Transactional`.
- **Security:** API admin yêu cầu `ROLE_ADMIN`; API user chỉ truy cập dữ liệu của chính mình (IDOR check bằng `@AuthenticationPrincipal`).
- **Audit:** Điều chỉnh credit/admin ghi `system_logs` (module có sẵn).

---

## 11. Acceptance Criteria Checklist

- [ ] **AC-1 (Cấp credit mặc định):** User đăng ký mới → nhận `default_credits` theo role (STUDENT=100, TEACHER=500).
- [ ] **AC-2 (Backfill user cũ):** Sau deploy, mọi user cũ chưa có account → được cấp đủ credit mặc định (chạy 1 lần, idempotent).
- [ ] **AC-3 (Trừ credit theo token đầu ra):** Học sinh xin gợi ý (`STUDENT_HINT`): `estimate = max(1, ceil(maxToken/1000))` đặt chỗ → sau khi AI trả 432 token → `actual = max(1, ceil(432/1000)) = 1` → hoàn phần dư, sổ cái có `CONSUME` + `REFUND`.
- [ ] **AC-4 (Hết credit):** `balance < cost` → HTTP `402` + `errorCode INSUFFICIENT_CREDITS` + message hướng dẫn mua thêm.
- [ ] **AC-5 (AI lỗi không mất credit):** AI trả 429/500/timeout → `REFUND` ghi nhận, balance khôi phục.
- [ ] **AC-6 (Mua gói mock):** User mua gói → đơn `SUCCESS`, balance tăng đúng số credit gói, sổ cái có `PURCHASE`.
- [ ] **AC-7 (Idempotent nạp):** Gọi `complete` 2 lần cho 1 đơn → credit chỉ cộng 1 lần.
- [ ] **AC-8 (Admin cấu hình):** Admin sửa `cost_per_call`, `default_credits`, CRUD gói credit — hiệu lực ngay (cache evict).
- [ ] **AC-9 (ADMIN không bị trừ):** Admin gọi AI → không trừ credit.
- [ ] **AC-10 (Race condition):** 2 request song song khi còn đủ credit cho 1 lượt → chỉ 1 thành công, balance không âm.
- [ ] **AC-11 (Giữ nguyên MAX_HINTS=3):** Giới hạn 3 gợi ý/bài tập vẫn hoạt động song song với credit.
- [ ] **AC-12 (Fallback thiếu token):** Provider không trả `completionTokens` → chỉ trừ phí tối thiểu `cost_per_call`, không phạt thêm.
- [ ] **AC-13 (Settle hoàn phần dư):** AI trả token thấp hơn ước lượng → `REFUND` phần dư, `totalSpent` và số dư cập nhật đúng theo token thực tế.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Backend Unit Tests (`AiCreditServiceTest.java`, `CreditPurchaseServiceTest.java`, `PaymentGatewayTest.java`)
- [ ] **UT-BE-01:** `reserve_enoughBalance_shouldDeductAndRecordConsume()`
- [ ] **UT-BE-02:** `reserve_insufficientBalance_shouldThrowInsufficientCreditException()`
- [ ] **UT-BE-03:** `refund_onAiFailure_shouldRestoreBalanceAndRecordRefund()`
- [ ] **UT-BE-04:** `noCreditConfig_shouldSkipCharging()`
- [ ] **UT-BE-05:** `adminRole_shouldNotBeCharged()`
- [ ] **UT-BE-06:** `concurrentReserve_shouldNotGoNegative()`
- [ ] **UT-BE-07:** `grantDefault_onRegistration_shouldCreateAccountWithRoleDefault()`
- [ ] **UT-BE-08:** `backfill_existingUsersWithoutAccount_shouldGrantDefaultsOnce()`
- [ ] **UT-BE-09:** `completePurchase_idempotent_shouldCreditOnce()`
- [ ] **UT-BE-10:** `mockGateway_initiateAndVerify_shouldReturnSuccess()`
- [ ] **UT-BE-11:** `getOrCreateAccount_existingUser_shouldReturnExisting()`
- [ ] **UT-BE-12:** `computeCredits_3000Tokens_shouldChargeCeilOf3000Over1000=3()`
- [ ] **UT-BE-13:** `computeCredits_belowFloor_shouldChargeCostPerCall()`
- [ ] **UT-BE-14:** `computeCredits_missingTokens_shouldFallbackToCostPerCall()`
- [ ] **UT-BE-15:** `settle_actualLessThanEstimate_shouldRefundExcess()`
- [ ] **UT-BE-16:** `openAiStrategy_parsesUsageCompletionTokens()`
- [ ] **UT-BE-17:** `geminiStrategy_parsesCandidatesTokenCount()`

### 12.2. Backend Integration Tests
- [ ] **IT-BE-01:** `POST /api/v1/submissions/{assignmentId}/hints` hết credit → `402 INSUFFICIENT_CREDITS`.
- [ ] **IT-BE-02:** `POST /api/v1/credits/purchase` + `complete` với `ROLE_STUDENT` → balance tăng đúng.
- [ ] **IT-BE-03:** `GET /api/v1/credits/me` chỉ trả dữ liệu của user hiện tại (IDOR check → `403`).
- [ ] **IT-BE-04:** `PUT /api/v1/admin/ai-credit-config/tasks/{task}` với `ROLE_TEACHER` → `403`.
- [ ] **IT-BE-05:** `POST /api/v1/admin/credits/adjust` với `ROLE_ADMIN` → balance thay đổi + `system_logs` ghi nhận.

---

## 13. Implementation Checklist

- [ ] Entities + Repositories: `UserAiAccount`, `AiCreditDefault`, `AiCreditConfig` (`tokens_per_credit`), `CreditTransaction`, `CreditPackage`, `CreditPurchaseOrder`.
- [ ] `PaymentGateway` interface + `MockPaymentGateway` + `PaymentGatewayFactory`.
- [ ] `AiExecutionResult(content, completionTokens)` + `AiProviderStrategy` đổi return type.
- [ ] Parse `usage.completion_tokens` (OpenAI) & `usageMetadata.candidatesTokenCount` (Gemini) + helper tách riêng.
- [ ] `AiCreditService` (reserve/refund/settle/estimateCredits/computeCredits/grantDefault/backfill) + `CreditPurchaseService`.
- [ ] Overload `AiPromptExecutionService.executePrompt(task, prompt, userId)` + enforce credit theo token (Reserve→Settle).
- [ ] Cập nhật `SubmissionHintServiceImpl`, `AiGradingServiceImpl` truyền `userId`.
- [ ] `InsufficientCreditException` (402) + handler trong `GlobalExceptionHandler`.
- [ ] Controllers: `CreditController` (user), `AdminCreditConfigController`, `AdminCreditPackageController`, `AdminCreditAdjustController`.
- [ ] DTOs + validation + cache evict.
- [ ] Seeder: defaults/configs/packages + backfill.
- [ ] Ghi chú FE: hiển thị balance, badge chi phí, popup "Mua thêm credit", xử lý `402`.
- [ ] Unit & Integration Tests (mục 12).

```
