# Đặc tả Kỹ thuật Backend (Backend Specification)

**Chức năng:** Quên & Đặt lại mật khẩu (Forgot & Reset Password)

---

## 1. Tech Stack & Cấu trúc Thư mục

* **Framework:** Java 21 & Spring Boot 4 (Spring Web, Spring Security, Spring Data JPA).
* **Database:** PostgreSQL 16.
* **Email Sending:** `JavaMailSender` kết hợp với Thymeleaf làm HTML template.
* **Dependencies bảo mật & bổ sung:** `Lombok`, `SecureRandom`, `BCryptPasswordEncoder`, `Bucket4j` (hoặc Redis) để áp dụng Rate Limiting.

### Cấu trúc file dự kiến

* `SecurityConfig.java`: Cấu hình cho phép truy cập public các endpoint quên mật khẩu và OAuth2.
* `AuthController.java`: Định nghĩa REST endpoints (`/api/auth/forgot-password`, `/api/auth/reset-password`).
* `AuthServiceImpl.java`: Xử lý logic sinh token, băm token, kiểm tra hạn mức gửi mail, mã hóa mật khẩu.
* `PasswordResetToken.java`: Entity quản lý token trong database.
* Các DTOs: `ForgotPasswordRequest`, `ResetPasswordRequest`, `MessageResponse`.

---

## 2. Database Schema (Thiết kế thực thể)

Để đảm bảo bảo mật và tách biệt dữ liệu, thông tin token không lưu trực tiếp vào bảng `users` mà được quản lý độc lập tại bảng `password_reset_tokens`.

### Bảng `users` (Chỉ hiển thị các trường liên quan)

* `id`: BIGSERIAL (Primary Key)
* `email`: VARCHAR(255) UNIQUE NOT NULL
* `password`: VARCHAR(255) NULLABLE *(Có thể null nếu đăng ký thuần bằng Google và chưa tạo mật khẩu phụ)*
* `provider`: VARCHAR(20) DEFAULT 'LOCAL' *(Nhận giá trị: `LOCAL` hoặc `GOOGLE`)*

### Bảng `password_reset_tokens`

* `id`: BIGSERIAL (Primary Key)
* `user_id`: BIGINT NOT NULL *(Foreign Key liên kết với `users.id`, ON DELETE CASCADE)*
* `token_hash`: VARCHAR(64) UNIQUE NOT NULL *(Lưu chuỗi token dưới dạng hash **SHA-256** để tránh lộ token plain-text khi bị rò rỉ database)*
* `expiry_date`: TIMESTAMP NOT NULL *(Thời gian tạo + 15 phút)*
* `is_used`: BOOLEAN DEFAULT FALSE

---

## 3. Quy tắc Nghiệp vụ chi tiết (Business & Security Logic)

### Luồng 1: Yêu cầu Quên mật khẩu (`POST /api/auth/forgot-password`)

1. **Validate dữ liệu đầu vào:** Kiểm tra định dạng trường `email` qua `@Email` và `@NotBlank`. Nếu không hợp lệ, trả về HTTP 400.
2. **Áp dụng Rate Limiting (Chống Spam Mail):** Kiểm tra tần suất gửi request dựa trên địa chỉ email (hoặc IP). **Giới hạn tối đa 1 request / 60 giây**. Nếu vượt quá, trả về HTTP 429.
3. **Truy vấn hệ thống:** Tìm kiếm tài khoản trong DB theo email.
    * **Trường hợp không tìm thấy Email:** Hệ thống **không làm gì thêm** (không sinh token, không gửi mail) nhưng **vẫn lập tức trả về HTTP 200 OK** kèm thông báo thành công chung để ngăn chặn lỗ hổng dò quét tài khoản (User Enumeration).
4. **Xử lý Giao thoa với Google Account:**
    * Hệ thống **cho phép** tài khoản có `provider = GOOGLE` thiết lập mật khẩu local phụ để đăng nhập đa nền tảng. Luồng xử lý tiếp tục bình thường như tài khoản `LOCAL`.
5. **Sinh mã Token an toàn (Plain-text):** Sử dụng `SecureRandom` kết hợp mã hóa Base64 an toàn cho URL để tạo ra chuỗi `rawToken` ngẫu nhiên, độ dài tối thiểu 32 bytes.
6. **Mã hóa và Lưu trữ vào DB:**
    * Băm chuỗi `rawToken` bằng thuật toán **SHA-256** để tạo ra `tokenHash`.
    * Lưu một bản ghi mới (hoặc ghi đè bản ghi cũ chưa sử dụng của user đó) vào bảng `password_reset_tokens` kèm `expiry_date` (15 phút sau).
7. **Gửi Email Bất đồng bộ (Asynchronous):**
    * Sử dụng `@Async` để xử lý tác vụ gửi mail dưới nền, không làm nghẽn luồng xử lý API chính.
    * Xây dựng đường dẫn (Link khôi phục): `${app.frontend-url}/reset-password?token=` + `rawToken`.
    * Chèn link vào template Thymeleaf và gửi thông qua `JavaMailSender`.

### Luồng 2: Đặt lại mật khẩu mới (`POST /api/auth/reset-password`)

1. **Validate dữ liệu đầu vào:** `@NotBlank` cho `token` và `newPassword`. Trường `newPassword` bắt buộc phải khớp với Regex quy định độ mạnh của mật khẩu hệ thống *(Ví dụ: tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường và 1 số)*.
2. **Xử lý và Tìm kiếm Token:**
    * Băm chuỗi `token` nhận được từ Frontend (Plain-text lấy từ URL) bằng **SHA-256**.
    * Tìm kiếm bản ghi trong bảng `password_reset_tokens` trùng khớp với giá trị `token_hash` vừa băm và có trạng thái `is_used = false`.
    * Nếu không tìm thấy, ném lỗi `BadRequestException` (HTTP 400): *"Token không hợp lệ hoặc đã qua sử dụng."*
3. **Kiểm tra thời gian hiệu lực:**
    * So sánh thời gian hiện tại với `expiry_date` của bản ghi.
    * Nếu thời gian hiện tại lớn hơn `expiry_date`, ném lỗi `BadRequestException` (HTTP 400): *"Đường dẫn đặt lại mật khẩu đã hết hạn."*
4. **Cập nhật dữ liệu (Đóng gói trong `@Transactional`):**
    * Tìm thực thể `User` tương ứng qua `user_id`.
    * Mã hóa mật khẩu mới (`newPassword`) bằng `BCryptPasswordEncoder`.
    * Cập nhật trường `password` của `User` bằng chuỗi đã mã hóa. *(Lưu ý: Đối với Google Account, hành động này sẽ cấp thêm mật khẩu cục bộ cho họ mà không làm mất liên kết Google).*
    * Vô hiệu hóa token: Đổi trạng thái bản ghi token thành `is_used = true` để ngăn chặn tấn công tái sử dụng (Replay Attack). Trả về HTTP 200 OK.

---

## 4. Đặc tả API (API Contracts)

### API 1: Yêu cầu gửi mail khôi phục mật khẩu

* **Endpoint:** `POST /api/auth/forgot-password`
* **Content-Type:** `application/json`
* **Request Body:**

```json
{
  "email": "user@example.com"
}

```

* **Response Thành công (HTTP 200 OK):**
*(Trả về chung cho cả email tồn tại và không tồn tại)*

```json
{
  "message": "Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư."
}

```

### API 2: Thực hiện đặt lại mật khẩu mới

* **Endpoint:** `POST /api/auth/reset-password`
* **Content-Type:** `application/json`
* **Request Body:**

```json
{
  "token": "raw-token-extracted-from-url-by-frontend",
  "newPassword": "SecurePassword123!"
}

```

* **Response Thành công (HTTP 200 OK):**

```json
{
  "message": "Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới."
}

```

---

## 5. Global Exception Handling (Danh mục mã lỗi trả về)

Hệ thống bắt lỗi tập trung tại `@RestControllerAdvice` và trả về cấu trúc thống nhất cho Frontend:

| Exception | HTTP Status | Cấu trúc dữ liệu Response | Tình huống xảy ra |
| --- | --- | --- | --- |
| `MethodArgumentNotValidException` | 400 Bad Request | `{"errors": {"newPassword": "Mật khẩu không đủ độ mạnh theo quy định"}}` | Điền trống trường hoặc mật khẩu mới sai định dạng quy định. |
| `BadRequestException` | 400 Bad Request | `{"message": "Mã xác nhận không hợp lệ hoặc đã hết hạn."}` | Token sai, token đã bị đổi `is_used = true`, hoặc quá hạn 15 phút. |
| `TooManyRequestsException` | 429 Too Many Requests | `{"message": "Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút."}` | Gửi liên tiếp nhiều request quên mật khẩu cho cùng một email trong vòng dưới 60 giây. |
| `InternalServerError` | 500 Internal Server | `{"message": "Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau."}` | Lỗi kết nối Database, lỗi cấu hình Mail Server (`MailSendException`). |
