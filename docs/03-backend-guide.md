# Hướng Dẫn Phát Triển (Backend Guide)

## 1. Cấu trúc Thư mục Chuẩn (Package Structure)

Dự án được cấu trúc theo hướng Domain-driven kết hợp Layered Architecture bên trong từng module:

```
src/main/java/com/codegym/mathclass/
 ├── auth/         # Module Đăng nhập, Đăng ký, OAuth2, Email OTP, Reset Password
 ├── user/         # Quản lý User profile, Avatar, Admin user & phân quyền
 ├── classroom/    # Quản lý Lớp học, Thành viên & Yêu cầu gia nhập (Join Requests)
 ├── assignment/   # Quản lý Bài tập, Giao bài cho lớp & Bóc tách tài liệu DOCX/PDF
 ├── submission/   # Bài nộp sinh viên, Chấm điểm, Nhận xét & Bản vẽ Canvas (Drawing)
 ├── dashboard/    # Báo cáo thống kê cho Giáo viên & Học sinh (Stats & At-risk analytics)
 ├── notification/ # Hệ thống thông báo thời gian thực (SSE Stream) & Settings
 ├── systemlog/    # Nhật ký hoạt động hệ thống cho Quản trị viên (Admin Audit Logs)
 ├── common/       # Cấu trúc Response chuẩn (`ApiResponse`, `PageResponse`)
 ├── config/       # Các Cấu hình hệ thống (Security, CORS, Cache, Async, Dotenv)
 ├── security/     # Xử lý JWT (Cookie & Header), CustomUserDetails, Security Filter
 ├── exception/    # Custom Exceptions & Global Exception Handler
 └── utils/        # Các tiện ích (LaTeXSanitizer, Supabase Storage Util, File Parsers)
```

**Chi tiết cấu trúc bên trong mỗi Domain Module:**

- `entity/`: Các Java class ánh xạ bảng Database (`@Entity`).
- `dto/`: Lớp chứa dữ liệu chuyển đổi Request/Response (`*RequestDTO`, `*ResponseDTO`).
- `repository/`: Các Interface truy vấn dữ liệu kế thừa `JpaRepository`.
- `service/`: Interface định nghĩa nghiệp vụ và lớp `impl/` thực thi logic.
- `controller/`: REST APIs (`@RestController`) xử lý HTTP Requests.

## 2. Quy tắc Lập trình (Coding Conventions)

### 2.1 Dependency Injection

- KHÔNG sử dụng tiêm phụ thuộc trực tiếp bằng `@Autowired` trên field.
- Sử dụng `@RequiredArgsConstructor` của Lombok kết hợp khai báo các dependency dạng `private final`.

### 2.2 Quy định về DTO và API Response

- Lớp Entity CHỈ dùng cho giao tiếp DB và lớp Service, KHÔNG trả Entity trực tiếp về Client.
- Mọi API thành công trả về wrapper chuẩn `ApiResponse<T>` hoặc `PageResponse<T>`.
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

## 3. Quy trình Làm việc với Git

- Nhánh chính: `main` (Production), `dev` (Development).
- Quy chuẩn tên nhánh tính năng: `feature/tên-tính-năng` hoặc `fix/tên-bug`.
- Đảm bảo mã nguồn biên dịch thành công (`./gradlew test`) trước khi tạo Pull Request.

