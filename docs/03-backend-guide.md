# Hướng Dẫn Phát Triển (Backend Guide)

## 1. Cấu trúc thư mục chuẩn (Package Structure)

Dự án được chia theo nghiệp vụ (Domain-driven):

```
src/main/java/com/codegym/mathclass/
 ├── auth/         # Module đăng nhập, đăng ký
 ├── user/         # Quản lý người dùng
 ├── classroom/    # Lớp học
 ├── assignment/   # Bài tập
 ├── submission/   # Nộp bài
 ├── dashboard/    # Thống kê
 ├── config/       # Các file Configuration chung
 ├── security/     # Cấu hình JWT & Phân quyền
 ├── exception/    # Custom Exceptions & Global Exception Handler
 └── utils/        # Các tiện ích (LaTeXSanitizer, utils)
```

**Bên trong mỗi module nghiệp vụ:**

- `entity/`: Các lớp ánh xạ bảng DB (`@Entity`).
- `dto/`: Dữ liệu vào/ra (`RequestDTO`, `ResponseDTO`).
- `repository/`: Các interface mở rộng `JpaRepository`.
- `service/`: Giao diện và Implementation logic.
- `controller/`: REST APIs (`@RestController`).

## 2. Quy tắc Coding (Coding Conventions)

### 2.1 Dependency Injection

- KHÔNG sử dụng `@Autowired` trên field.
- Sử dụng `@RequiredArgsConstructor` của Lombok và đánh dấu các dependency là `private final`.

### 2.2 DTO mapping

- Entity chỉ dùng để giao tiếp với DB.
- Mọi dữ liệu trả về client phải là **ResponseDTO**.
- Mọi dữ liệu nhận từ client phải là **RequestDTO** kèm theo valid (`@NotBlank`, `@NotNull`, ...).

### 2.3 Exception Handling

- Throw các Custom Exception khi có lỗi logic (VD: `ResourceNotFoundException`, `BadRequestException`).
- Không trả về thẳng `ResponseEntity` lỗi ở Controller, hãy ném Exception và để lớp `GlobalExceptionHandler` xử lý chuẩn hóa output JSON.

### 2.4 Bảo mật Toán Học

- Mọi input từ người dùng có chứa công thức toán phải được đi qua `LaTeXSanitizer` để loại bỏ mã độc LaTeX.

### 2.5 Kiểu dữ liệu đặc thù

- Cột lưu JSON (ví dụ hình vẽ JSXGraph) cần sử dụng:
  `@Column(columnDefinition = "jsonb")`

## 3. Quy trình làm việc với Git

- Nhánh chính: `main`, `dev`.
- Tạo nhánh mới từ nhánh chính cho từng tính năng: `feature/tên-tính-năng`.
- Đảm bảo code pass hết lint và test (nếu có) trước khi tạo Pull Request.
