# Hướng Dẫn Phát Triển (Backend Guide)

## 1. Cấu trúc Thư mục Chuẩn (Package Structure)

Dự án được cấu trúc theo hướng Domain-driven kết hợp Layered Architecture bên trong từng module:

```
src/main/java/com/codegym/mathclass/
 ├── auth/         # Module Đăng nhập, Đăng ký, 2FA TOTP, OAuth2, Email OTP, Reset Password
 ├── user/         # Quản lý User profile, Avatar, Admin user & phân quyền Roles/Permissions
 ├── classroom/    # Quản lý Lớp học, Thành viên, Nhận xét học sinh & Yêu cầu gia nhập (Join Requests)
 ├── assignment/   # Quản lý Bài tập, Phiếu bài tập, Thư viện chia sẻ, Tags & Bóc tách DOCX/PDF
 ├── submission/   # Bài nộp học sinh, Chấm điểm, Nhận xét câu hỏi & Bản vẽ Canvas (Drawing)
 ├── dashboard/    # Báo cáo thống kê cho Giáo viên & Học sinh (Stats & At-risk analytics)
 ├── notification/ # Hệ thống thông báo thời gian thực (SSE Stream) & Cấu hình Settings
 ├── chat/         # Hệ thống tin nhắn thời gian thực WebSocket STOMP (Lớp học & 1-1)
 ├── aiconfig/     # Quản trị AI Providers, API Keys (AES-256), Task Routing, Prompts, Credit & Thanh toán VietQR
 ├── aiqueue/      # Hàng đợi xử lý tác vụ AI bất đồng bộ với Redisson Blocking Queue
 ├── bugreport/    # Tiếp nhận và xử lý báo cáo sự cố từ người dùng
 ├── systemlog/    # Nhật ký hoạt động hệ thống cho Quản trị viên (Admin Audit Logs)
 ├── storage/      # Quản lý tệp tin và dọn dẹp Supabase Cloud Storage
 ├── common/       # Cấu trúc chung (`BaseEntity`, `ApiResponse`, `PageResponse`, Annotations)
 ├── config/       # Cấu hình hệ thống (Security, CORS, Cache, Async, Dotenv, Redis)
 ├── security/     # Xử lý JWT (Cookie & Header), CustomUserDetails, Security Filter, Interceptor
 ├── exception/    # Custom Exceptions & Global Exception Handler
 └── utils/        # Các tiện ích (LaTeXSanitizer, Supabase Storage Util, File Parsers, Mã hóa AES)
```

**Chi tiết cấu trúc bên trong mỗi Domain Module:**

- `entity/`: Các Java class ánh xạ bảng Database (`@Entity`), kế thừa từ `BaseEntity`.
- `dto/`: Phân tách tường minh thành 2 package con:
  - `dto/request/`: Chứa các Request DTO có hậu tố `*Request` (ví dụ: `CreateAssignmentRequest`, `LoginRequest`).
  - `dto/response/`: Chứa các Response DTO có hậu tố `*Response` (ví dụ: `AssignmentResponse`, `UserResponse`).
  - *Tuyệt đối KHÔNG sử dụng hậu tố `*DTO` hoặc `*Dto`*.
- `repository/`: Các Interface truy vấn dữ liệu kế thừa `JpaRepository` / `JpaSpecificationExecutor`.
- `service/`: Interface định nghĩa nghiệp vụ và lớp `impl/` thực thi logic (`@Transactional` cho các hàm ghi).
- `controller/`: REST APIs (`@RestController`) xử lý HTTP Requests và validate bằng `@Valid`.

## 2. Quy tắc Lập trình (Coding Conventions)

### 2.1 Dependency Injection

- KHÔNG sử dụng tiêm phụ thuộc trực tiếp bằng `@Autowired` trên field.
- Sử dụng `@RequiredArgsConstructor` của Lombok kết hợp khai báo các dependency dạng `private final`.

### 2.2 Quy định về DTO và API Response

- Lớp Entity CHỈ dùng cho giao tiếp DB và lớp Service, KHÔNG trả Entity trực tiếp về Client.
- Dữ liệu trả về Client được map qua DTO Response (trả về trực tiếp trong `ResponseEntity<T>` hoặc bọc `ResponseEntity<ApiResponse<T>>` tùy theo quy định module).
- Mọi dữ liệu nhận từ Client phải bọc trong Request DTO kèm validation annotations (`@NotBlank`, `@NotNull`, `@Min`, ...).


### 2.3 Quản lý Ngoại lệ (Exception Handling)

- Ném Custom Exception khi gặp lỗi logic nghiệp vụ (ví dụ: `ResourceNotFoundException`, `BadRequestException`, `UnauthorizedException`).
- Không bắt exception rồi trả về `ResponseEntity.badRequest()` trong Controller. Tất cả exception sẽ tự động được bắt và định dạng lại bởi `GlobalExceptionHandler`.

### 2.4 Bảo mật Nội dung Toán học (LaTeX & Drawing Data)

- Mọi công thức Toán học nhận từ phía Client phải đi qua `LaTeXSanitizer` để loại bỏ các đoạn mã độc/XSS.
- Đối với dữ liệu bản vẽ Canvas / JSXGraph, sử dụng kiểu lưu trữ JSONB PostgreSQL (`@JdbcTypeCode(SqlTypes.JSON)` hoặc `@Column(columnDefinition = "jsonb")`).

### 2.5 Xác thực Token & Session Cookie

- Hệ thống hỗ trợ song song 2 cơ chế đọc JWT Token:
  1. Cookie bảo mật `HTTP-Only` (`mathclass_jwt`).
  2. HTTP Header `Authorization: Bearer <token>`.
- Hệ thống hỗ trợ cơ chế Refresh Token xoay vòng (Rotated Refresh Token) để gia hạn phiên đăng nhập an toàn.

### 2.6 Kiến trúc Xác thực Hai Yếu Tố (2FA - TOTP RFC 6238)

- **Bắt buộc cho Quản trị viên (`ADMIN`):** Tuyệt đối không cấp Access Token hay Cookie phiên khi đăng nhập mật khẩu thành công nếu tài khoản là Admin.
- **Pre-Auth Token Pattern:** Cấp JWT ngắn hạn (TTL 5 phút, scope `PRE_AUTH`). `AuthTokenFilter` chặn tất cả API tài nguyên nếu token mang scope này.
- **Bảo vệ Khóa bí mật & Mã dự phòng:** Tách biệt `temp_secret_key` và `secret_key` trong `user_two_factor_auth`; toàn bộ mã dự phòng (Backup Codes) bắt buộc phải được băm một chiều bằng BCrypt trong bảng `user_backup_codes`.
- **Phòng vệ an ninh:** Áp dụng chống Replay Attack (Cache 60s), dung sai Clock Drift $\pm 30$s, và Rate Limiting tối đa 5 lần sai (khóa 15 phút).
- 📖 Tham khảo chi tiết tại [Hướng dẫn 2FA (06-two-factor-authentication.md)](06-two-factor-authentication.md).

### 2.7 Quy Chuẩn Phát Triển & Tích Hợp AI Services

- **Mã hóa Khóa API (AES-256-GCM):** Mọi API Key phải đi qua `AesGcmEncryptionService`, không in key dạng Plaintext ra console, log hay response DTO.
- **Cơ chế Reserve-then-Refund & Khóa Bi Quan:**
  - Bắt buộc dùng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên `user_ai_accounts` khi đặt chỗ trừ credit.
  - Khi AI lỗi hoặc token thực tế nhỏ hơn mức ước lượng, bắt buộc gọi `refund()` trong transaction để hoàn lại credit cho người dùng.
- **Bất biến Sổ Cái Giao Dịch:** Tuyệt đối không cập nhật (`UPDATE`) hay xóa (`DELETE`) các bản ghi trong `credit_transactions`.
- **Cache Task Routing & Prompts:** Sử dụng Spring Cache với Redis (Redisson). Mọi thao tác cập nhật cấu hình phải có `@CacheEvict`.
- **Xử lý Hết Credit:** Ném `InsufficientCreditException` để GlobalExceptionHandler định dạng thành HTTP Status `402 Payment Required` kèm `errorCode = "INSUFFICIENT_CREDITS"`.
- 📖 Tham khảo chi tiết tại [Hướng dẫn Hệ thống AI & Credit Quota (07-ai-subsystem.md)](07-ai-subsystem.md).

## 3. Quy trình Làm việc với Git

- Nhánh chính: `main` (Production), `dev` (Development).
- Quy chuẩn tên nhánh tính năng: `feature/tên-tính-năng` hoặc `fix/tên-bug`.
- Đảm bảo mã nguồn biên dịch thành công (`./gradlew test`) trước khi tạo Pull Request.



