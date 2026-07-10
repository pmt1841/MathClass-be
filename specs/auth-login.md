# Spec: Chức năng Xác thực & Đăng nhập (Authentication & Login)

## Objective
Hệ thống cần cung cấp giải pháp xác thực người dùng an toàn, hỗ trợ hai phương thức đăng nhập:
1. Đăng nhập truyền thống bằng tài khoản (Email & Mật khẩu).
2. Đăng nhập thông qua tài khoản Google (Google OAuth2).

Sau khi xác thực thành công, hệ thống sẽ cấp phát một JSON Web Token (JWT) stateless để Client sử dụng truy cập vào các tài nguyên được bảo vệ.

---

## Tech Stack
*   **Java 21** & **Spring Boot 3** (Spring Web, Spring Security, Spring Data JPA).
*   **Database:** PostgreSQL 16 (lưu trữ thông tin người dùng `User`, vai trò `Role` và cấu hình thông báo `NotificationSettings`).
*   **Security:** JSON Web Token (JWT) làm cơ chế xác thực stateless; BCrypt để mã hóa mật khẩu.
*   **OAuth2:** Google OAuth2 API Client để xác minh token nhận được từ Google.

---

## Commands
*   Khởi chạy môi trường Docker: `docker-compose up --build`
*   Khởi chạy backend cục bộ: `./gradlew bootRun`
*   Build dự án: `./gradlew build`
*   Chạy kiểm thử: `./gradlew test`

---

## Project Structure
Các thành phần liên quan trực tiếp đến tính năng đăng nhập nằm ở các tệp nguồn sau:
*   [SecurityConfig.java](../src/main/java/com/codegym/mathclass/security/config/SecurityConfig.java) -> Cấu hình Spring Security, phân quyền cho endpoint `/api/auth/**` ở chế độ public.
*   [AuthController.java](../src/main/java/com/codegym/mathclass/auth/controller/AuthController.java) -> REST Controller công khai các REST endpoints cho client gọi.
*   [AuthService.java](../src/main/java/com/codegym/mathclass/auth/service/AuthService.java) & [AuthServiceImpl.java](../src/main/java/com/codegym/mathclass/auth/service/impl/AuthServiceImpl.java) -> Quản lý nghiệp vụ xác thực email/password và Google token.
*   [LoginRequest.java](../src/main/java/com/codegym/mathclass/auth/dto/request/LoginRequest.java) -> DTO dữ liệu đầu vào đối với đăng nhập email/mật khẩu.
*   [GoogleAuthRequest.java](../src/main/java/com/codegym/mathclass/auth/dto/request/GoogleAuthRequest.java) -> DTO dữ liệu đầu vào cho đăng nhập bằng Google.
*   [JwtResponse.java](../src/main/java/com/codegym/mathclass/auth/dto/response/JwtResponse.java) -> DTO phản hồi trả về khi xác thực thành công.

---

## Code Style
*   Sử dụng **Lombok** `@Getter`, `@Setter` cho DTOs và `@RequiredArgsConstructor` để tự động inject dependencies qua constructor trong Spring.
*   Validate dữ liệu đầu vào trực tiếp bằng các annotation `@NotBlank`, `@Email`, `@Size` trên các thuộc tính của DTO.
*   Bắt lỗi tập trung (Global Exception Handling) và ném ra các RuntimeException chuẩn (như `BadRequestException`) khi xảy ra lỗi xác thực hoặc lỗi dữ liệu đầu vào.

Mẫu khai báo Request DTO:
```java
@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải tối thiểu 6 ký tự")
    @Size(max = 24, message = "Mật khẩu không quá 24 ký tự")
    private String password;
}
```

---

## Testing Strategy
*   Sử dụng framework **JUnit 5** và thư viện mock **Mockito**.
*   **Unit Tests:** Kiểm thử chi tiết cho [AuthServiceImpl.java](../src/main/java/com/codegym/mathclass/auth/service/impl/AuthServiceImpl.java) với các trường hợp: đăng nhập thành công, đăng nhập thất bại do sai mật khẩu, đăng nhập thất bại do tài khoản chưa kích hoạt.
*   **Integration Tests:** Sử dụng `@WebMvcTest` hoặc `@SpringBootTest` kết hợp `MockMvc` để gửi yêu cầu giả lập đến `/api/auth/login` và kiểm tra phản hồi HTTP Status, dữ liệu trả về.

---

## Boundaries
*   **Always:**
    *   Validate định dạng email và độ dài mật khẩu ở tầng Controller trước khi xử lý nghiệp vụ.
    *   Mã hóa mật khẩu bằng `BCryptPasswordEncoder` trước khi lưu vào DB hoặc khi so sánh mật khẩu.
    *   Cấp phát JWT token có thời gian hết hạn cụ thể (Expiration Time).
*   **Ask first:**
    *   Thay đổi thời hạn hiệu lực của JWT token hoặc thuật toán ký số.
    *   Cấu hình thêm các CORS origins khác ngoài cấu hình mặc định tại [SecurityConfig.java](../src/main/java/com/codegym/mathclass/security/config/SecurityConfig.java).
*   **Never:**
    *   Trả về mật khẩu hash hoặc thông tin nhạy cảm của người dùng trong payload phản hồi `JwtResponse`.
    *   Lưu thông tin đăng nhập thô hoặc ghi log chứa mật khẩu của người dùng.

---

## Success Criteria (Tiêu chí thành công & Chi tiết API)

### 1. Đăng nhập qua Email và Mật khẩu
*   **Endpoint:** `POST /api/auth/login`
*   **Headers:** `Content-Type: application/json`
*   **Request Body:**
    ```json
    {
      "email": "user@example.com",
      "password": "securepassword"
    }
    ```
*   **Tiêu chí thành công (200 OK):**
    *   Trả về JWT Token và thông tin cơ bản của người dùng:
        ```json
        {
          "token": "eyJhbGciOiJIUzUxMiJ9...",
          "type": "Bearer",
          "id": 1,
          "email": "user@example.com",
          "fullName": "Nguyen Van A",
          "userRole": "STUDENT",
          "avatarUrl": "https://example.com/avatar.png"
        }
        ```
*   **Các kịch bản lỗi:**
    *   **400 Bad Request:** Lỗi do dữ liệu đầu vào không hợp lệ (ví dụ: email trống hoặc sai định dạng).
    *   **401 Unauthorized:** Lỗi do sai thông tin đăng nhập (email không tồn tại hoặc sai mật khẩu).

### 2. Đăng nhập qua Google (OAuth2)
*   **Endpoint:** `POST /api/auth/google`
*   **Request Body:**
    ```json
    {
      "credential": "google-id-token-received-from-frontend",
      "role": "STUDENT"
    }
    ```
*   **Tiêu chí thành công (200 OK):**
    *   Xác minh token của Google qua API Google thành công.
    *   Nếu email đã tồn tại: Tiến hành đăng nhập và trả về `JwtResponse`.
    *   Nếu email chưa tồn tại: Tự động đăng ký tài khoản mới với vai trò được truyền lên (`role` mặc định là `STUDENT`), kích hoạt trạng thái tài khoản hoạt động (`active = true`), tự động tạo cấu hình thông báo `NotificationSettings`, sau đó tiến hành đăng nhập và trả về `JwtResponse`.
*   **Các kịch bản lỗi:**
    *   **400 Bad Request:** Token Google không hợp lệ hoặc lỗi kết nối đến API xác thực Google.
