# Specification: Tích hợp Cổng Thanh toán VietQR & SePay Webhook Nạp Credit (`MathClass-service` & `MathClass-fe`)

---

## 1. Feature Overview
- **Feature Name:** Tích hợp Cổng Thanh toán Chuyển khoản VietQR & Webhook SePay Nạp AI Credit
- **Jira Ticket:** [MAT-361](https://phanvanluan611996.atlassian.net/browse/MAT-361)
- **Target Subsystems:** `MathClass-service` (Backend Spring Boot), `MathClass-fe` (Frontend Next.js)
- **Target Users:** Học sinh (Student), Giảng viên (Teacher), Quản trị viên (Admin)

---

## 2. Business Goal & Core Objectives

Thay thế cơ chế nạp credit "nhấn phát được luôn" (Mock) bằng **luồng thanh toán ngân hàng thực tế**:

1. **Hiển thị VietQR chuẩn Napas247:** Khi người dùng bấm mua gói credit, hệ thống hiển thị mã VietQR động với đầy đủ thông tin: Ngân hàng, Số tài khoản, Tên người thụ hưởng, Số tiền chính xác và Nội dung chuyển tiền định danh đơn (`MAT<orderId>`). Người dùng quét mã là ứng dụng ngân hàng tự động điền sẵn 100%, chỉ việc bấm xác nhận chuyển.
2. **Cấu hình Ngân hàng & Webhook bên Admin:** Quản trị viên có màn hình quản lý thiết lập tài khoản ngân hàng nhận tiền (Mã ngân hàng, Số tài khoản, Tên chủ tài khoản, Tiền tố cú pháp, API Key SePay).
3. **Tự động đối soát & cộng Credit qua SePay Webhook:** Khi người dùng chuyển khoản, dịch vụ SePay quét biến động số dư tài khoản ngân hàng và gửi Webhook về Backend. Backend xác thực bảo mật API Key, trích xuất mã đơn từ nội dung chuyển tiền, kiểm tra số tiền khớp và tự động cộng Credit cho người dùng ngay lập tức (realtime).
4. **Idempotency & Chống Double-Credit:** Xử lý đơn bằng bi quan khóa (`findByIdForUpdate`), đảm bảo dù Webhook gửi lại nhiều lần hoặc có race condition thì Credit chỉ được cộng đúng 1 lần duy nhất.
5. **Duyệt thủ công dự phòng cho Admin:** Có màn hình quản lý đơn nạp credit (`Credit Orders`) để Admin tra cứu trạng thái đơn, và hỗ trợ nút "Duyệt thủ công" trong trường hợp người dùng gõ sai cú pháp chuyển tiền.
6. **Kiến trúc & Hiệu năng:**
   - Tuyệt đối không để xảy ra lỗi truy vấn N+1 (truy vấn kèm DTO projection hoặc `JOIN FETCH`).
   - Import đầy đủ ở đầu file, không viết đường dẫn package trực tiếp trong thân code.

---

## 3. Potential Logic Loopholes & Mitigations (Edge Cases)

### 3.1. Case 1: Webhook Replay / Retry Attack (Trùng lặp Webhook)
- **Vấn đề:** SePay gửi lại webhook nhiều lần (do timeout mạng hoặc retry policy) hoặc kẻ gian bắt chước payload gửi nhiều lần.
- **Khắc phục:** 
  1. Kiểm tra API Key bí mật trong Header `Authorization: Apikey <SECRET_TOKEN>`.
  2. Khóa dòng bản ghi đơn hàng bằng `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`findByIdForUpdate`).
  3. Kiểm tra trạng thái đơn: Nếu `status != PENDING` (đã là `SUCCESS` hoặc `CANCELLED`), trả về ngay HTTP 200 `{"success": true, "message": "Order already processed"}` mà không cộng thêm credit (BR-3).

### 3.2. Case 2: Chuyển khoản thiếu tiền hoặc sai số tiền
- **Vấn đề:** Đơn hàng giá 50.000 VNĐ nhưng người dùng sửa số tiền còn 20.000 VNĐ.
- **Khắc phục:** Webhook handler so sánh `transferAmount >= order.getPrice()`. Nếu nhỏ hơn, đánh dấu cảnh báo giao dịch thiếu tiền, không tự động cộng credit, giữ trạng thái `PENDING` để Admin xử lý thủ công.

### 3.3. Case 3: Chuyển khoản sai cú pháp nội dung
- **Vấn đề:** Cú pháp quy định `MAT102`, nhưng khách hàng chuyển khoản ghi `naptienhoc` hoặc không có nội dung.
- **Khắc phục:** 
  1. Mã VietQR đã nhúng sẵn nội dung vào mã QR, khách quét app ngân hàng sẽ tự điền không cần gõ tay.
  2. Regex bóc tách nội dung linh hoạt: `(?i)MAT\s*(\d+)` (chấp nhận cả `MAT102` và `MAT 102`).
  3. Nếu không bóc tách được mã đơn hợp lệ: Ghi log giao dịch chưa rõ đơn để Admin vào tra cứu và bấm "Duyệt thủ công" bù cho khách.

### 3.4. Case 4: Chuyển tiền sau khi đơn hết hạn (Expired Order)
- **Vấn đề:** Đơn hàng có hạn thanh toán 15 phút. Khách để 30 phút sau mới chuyển tiền.
- **Khắc phục:** Nếu đơn đã hết hạn nhưng kiểm tra ngân hàng thực sự đã nhận tiền và chưa được xử lý, hệ thống vẫn cho phép kích hoạt cộng credit hợp lệ và log lại thời điểm hoàn thành để bảo vệ quyền lợi người dùng.

### 3.5. Case 5: Vấn đề N+1 Query khi Admin xem danh sách đơn nạp
- **Vấn đề:** Khi Admin xem danh sách 50 đơn nạp credit, mỗi đơn lại query thêm bảng `users` để lấy tên/email dẫn đến 51 câu query N+1.
- **Khắc phục:** Sử dụng JPQL Query DTO Projection hoặc `JOIN FETCH` trong Repository (`@Query("SELECT new com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderAdminResponse(...) FROM CreditPurchaseOrder o JOIN User u ON o.userId = u.id ...")`) đảm bảo chỉ chạy 1 câu truy vấn duy nhất.

---

## 4. Functional Requirements

- **FR-1 (Payment Configuration Entity & API):**
  - Quản lý cấu hình ngân hàng: `bankCode`, `accountNumber`, `accountHolderName`, `sepayApiKey`, `transferSyntaxPrefix`, `isActive`, `qrTemplate`.
  - API Admin: `GET /api/v1/admin/payment-config`, `PUT /api/v1/admin/payment-config`.
  - API Public/User: `GET /api/v1/credits/payment-config` (chỉ trả về `bankCode`, `accountNumber`, `accountHolderName`, `transferSyntaxPrefix`, ẩn `sepayApiKey`).

- **FR-2 (Initiate VietQR Purchase Order):**
  - Khi user gọi `POST /api/v1/credits/purchase`: Tạo đơn `PENDING` và trả về chi tiết thanh toán kèm URL ảnh VietQR:
    `https://img.vietqr.io/image/{bankCode}-{accountNumber}-{qrTemplate}.png?amount={price}&addInfo={syntax}{orderId}&accountName={accountHolderName}`
  - Tích hợp thêm trường `qrUrl`, `transferSyntax`, `accountNumber`, `bankCode` vào `CreditPurchaseResponse`.

- **FR-3 (SePay Webhook Verification & Processing):**
  - Endpoint `POST /api/v1/payment/webhook/sepay` (công khai, không yêu cầu JWT người dùng).
  - Xác thực qua Header: `Authorization: Apikey <sepayApiKey>` (hoặc cấu hình secret token).
  - Trích xuất mã đơn `orderId`, đối soát số tiền, khóa bi quan bản ghi đơn hàng, cập nhật `SUCCESS`, cộng credit vào `UserAiAccount`, ghi log sổ cái `credit_transactions`.

- **FR-4 (Order Status Polling API):**
  - API `GET /api/v1/credits/purchase/orders/{orderId}/status` để Frontend kiểm tra trạng thái đơn nạp theo thời gian thực (realtime polling).

- **FR-5 (Admin Credit Orders Management & Manual Approval):**
  - API `GET /api/v1/admin/credit-orders`: Lấy danh sách tất cả đơn nạp kèm phân trang, tìm kiếm theo trạng thái/userId, chống N+1.
  - API `POST /api/v1/admin/credit-orders/{orderId}/approve`: Admin duyệt thủ công đơn nạp (chuyển PENDING → SUCCESS, cộng credit).

---

## 5. Business Rules

- **BR-1:** Mọi thao tác cập nhật số dư credit bắt buộc phải dùng transaction có khóa bi quan (`PESSIMISTIC_WRITE`).
- **BR-2 (Idempotency):** Đơn đã `SUCCESS` không bao giờ được cộng thêm credit lần thứ hai.
- **BR-3 (Bảo mật Webhook):** Webhook thiếu API key hoặc API key không khớp với cấu hình hệ thống sẽ bị từ chối với HTTP 401 Unauthorized.
- **BR-4 (Đúng số tiền):** Chỉ tự động hoàn thành đơn khi số tiền chuyển vào lớn hơn hoặc bằng giá niêm yết của gói credit (`transferAmount >= order.price`).
- **BR-5 (Clean Code & Import):**
  - Toàn bộ import khai báo ở đầu file. Tuyệt đối không dùng fully qualified class names trong code.
  - Đảm bảo triệt tiêu truy vấn N+1.
- **BR-6 (Mã hóa SePay API Key):**
  - Trường `sepayApiKey` bắt buộc phải được mã hóa/giải mã bằng chuẩn AES-256-GCM thông qua `ApiKeyCryptoConverter` (tái sử dụng chung với hệ thống AI Provider Key), lưu trong cột kiểu `TEXT`.

---

## 6. Data Model

### 6.1. Entity `PaymentConfig` (bảng `payment_configs`)
```sql
CREATE TABLE payment_configs (
    id BIGSERIAL PRIMARY KEY,
    bank_code VARCHAR(20) NOT NULL DEFAULT 'MB',
    account_number VARCHAR(50) NOT NULL DEFAULT '',
    account_holder_name VARCHAR(100) NOT NULL DEFAULT '',
    sepay_api_key TEXT, -- Mã hóa tự động AES-256-GCM qua ApiKeyCryptoConverter
    transfer_syntax_prefix VARCHAR(20) NOT NULL DEFAULT 'MAT',
    qr_template VARCHAR(20) NOT NULL DEFAULT 'compact2',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);
```

### 6.2. Entity `CreditPurchaseOrder` (bảng `credit_purchase_orders`)
Đã có sẵn trong hệ thống: `id`, `user_id`, `package_id`, `credits`, `price`, `gateway_code`, `status`, `transaction_ref`, `paid_at`.
Cổng `gateway_code` mặc định khi thanh toán qua ngân hàng sẽ là `"SEPAY_VIETQR"`.

---

## 7. REST API Endpoints

### 7.1. Cấu hình Thanh toán (Admin)
- `GET /api/v1/admin/payment-config`
  - Quyền: `ROLE_ADMIN`
  - Response: `PaymentConfigResponse` (đầy đủ cả `sepayApiKey`)
- `PUT /api/v1/admin/payment-config`
  - Quyền: `ROLE_ADMIN`
  - Request: `PaymentConfigUpdateRequest`
  - Response: `PaymentConfigResponse`

### 7.2. Lấy thông tin thanh toán (User)
- `GET /api/v1/credits/payment-config`
  - Quyền: Đã đăng nhập (`STUDENT`, `TEACHER`, `ADMIN`)
  - Response: `PublicPaymentConfigResponse` (chỉ gồm `bankCode`, `accountNumber`, `accountHolderName`, `transferSyntaxPrefix`, `isActive`)

### 7.3. Tạo & Kiểm tra đơn mua Credit
- `POST /api/v1/credits/purchase`
  - Request: `{"packageId": 1}`
  - Response: `CreditPurchaseResponse` (bổ sung `qrUrl`, `transferSyntax`, `accountNumber`, `bankCode`, `accountHolderName`)
- `GET /api/v1/credits/purchase/orders/{orderId}/status`
  - Response: `CreditOrderStatusResponse` (`orderId`, `status`, `creditsAdded`, `newBalance`, `paidAt`)

### 7.4. Webhook SePay (Public Endpoint)
- `POST /api/v1/payment/webhook/sepay`
  - Headers: `Authorization: Apikey <sepayApiKey>`
  - Body: `SepayWebhookPayload`
  - Response: `{"success": true, "message": "Processed successfully"}`

### 7.5. Quản lý Đơn nạp Credit (Admin)
- `GET /api/v1/admin/credit-orders?page=0&size=20&status=PENDING`
  - Quyền: `ROLE_ADMIN`
  - Response: `Page<CreditOrderAdminResponse>` (kèm thông tin user `fullName`, `email` lấy qua 1 câu JOIN query)
- `POST /api/v1/admin/credit-orders/{orderId}/approve`
  - Quyền: `ROLE_ADMIN`
  - Response: `CreditOrderAdminResponse`

---

## 8. Verification & Testing Strategy

### 8.1. Backend Test Cases (`MathClass-service`)
- `PaymentConfigServiceImplTest`:
  - Test lấy và cập nhật cấu hình thanh toán.
  - Test tạo URL VietQR đúng định dạng và mã hóa URL.
- `SepayWebhookControllerTest` & `SepayPaymentGatewayTest`:
  - Test từ chối webhook khi sai API key (401).
  - Test xử lý thành công webhook: bóc tách `MAT102`, kiểm tra số tiền, cập nhật đơn `SUCCESS`, cộng credit.
  - Test idempotent: webhook gọi lần 2 không cộng thêm credit.
  - Test số tiền chuyển nhỏ hơn giá đơn: không cộng credit.
- `AdminCreditOrderServiceTest`:
  - Test danh sách đơn không bị N+1 query.
  - Test duyệt thủ công đơn nạp thành công.

### 8.2. Frontend Test Cases (`MathClass-fe`)
- `CreditPurchaseModal.test.tsx`:
  - Hiển thị đầy đủ ảnh VietQR, số tài khoản, số tiền, cú pháp chuyển tiền.
  - Sao chép nhanh số tài khoản và cú pháp vào clipboard.
  - Tự động chuyển màn hình thành công khi nhận trạng thái `SUCCESS`.
- `AdminPaymentConfigTab.test.tsx`:
  - Hiển thị form cấu hình ngân hàng, cập nhật thành công.
