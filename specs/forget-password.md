# Spec: Chức năng Quên & Đặt lại mật khẩu (Forgot & Reset Password)

---

## Phần 1: Product Definition (Dành cho TẤT CẢ)

### Objective (Mục tiêu)

Tính năng này cung cấp giải pháp an toàn để người dùng khôi phục lại quyền truy cập vào tài khoản khi họ quên mật khẩu, hoặc khi họ đăng ký qua hệ thống bên thứ ba (như Google) nhưng nay muốn thiết lập mật khẩu truy cập trực tiếp.

### Tại sao phải làm?

- Đảm bảo người dùng không bị mất tài khoản vĩnh viễn khi quên mật khẩu.
- Cải thiện trải nghiệm người dùng bằng cách cung cấp quy trình tự động khôi phục nhanh chóng mà không cần liên hệ bộ phận hỗ trợ.
- Đảm bảo an toàn bảo mật, tránh việc kẻ gian đánh cắp tài khoản (bằng cách xác thực qua email chính chủ).

### User Flow (Luồng người dùng tổng quan)

1. **Yêu cầu khôi phục:** Người dùng nhập địa chỉ email vào form "Quên mật khẩu". Hệ thống kiểm tra và gửi một liên kết chứa mã xác nhận (token) có thời hạn qua email.
2. **Xác nhận qua email:** Người dùng mở hộp thư, click vào liên kết an toàn để chuyển đến màn hình đặt lại mật khẩu.
3. **Thiết lập mật khẩu mới:** Người dùng nhập mật khẩu mới. Hệ thống xác minh mã token và cập nhật mật khẩu mới vào cơ sở dữ liệu.

---

## Phần 2: Frontend Specification (Yêu cầu cho FE)

### Tech Stack

- **Framework:** Next.js 14+ (App Router), React 19.

- **Styling & UI:** Tailwind CSS, Radix UI (thông qua shadcn/ui).
- **Form Management:** React Hook Form kết hợp với Zod.
- **API & State:** Axios và TanStack React Query (useMutation).

### Project Structure (Dự kiến)

- `app/(auth)/forgot-password/page.tsx`: Màn hình nhập email yêu cầu quên mật khẩu.

- `app/(auth)/reset-password/page.tsx`: Màn hình đặt lại mật khẩu (đọc `token` từ URL parameters).
- `components/auth/forgot-password-form.tsx`: Component chứa logic gửi email.
- `components/auth/reset-password-form.tsx`: Component chứa logic đổi mật khẩu.
- `lib/api/auth.ts`: Nơi định nghĩa các hàm gọi API.
- `hooks/useAuth.ts` (hoặc hook riêng): Đóng gói logic React Query xử lý trạng thái loading/error.

### UX/UI & Code Style

- Sử dụng **React Hook Form** và **Zod** để validate (ví dụ: định dạng email, mật khẩu khớp nhau, độ dài tối thiểu) ngay tại client trước khi gọi API để phản hồi nhanh cho người dùng.

- Hiển thị thông báo (Toast message) rõ ràng bằng thư viện `sonner` hoặc UI Toast khi thao tác thành công hoặc thất bại.

### Boundaries (Giới hạn & Quy tắc)

- **Always (Luôn luôn):**
  - Hiển thị trạng thái loading (spinner/disabled button) trong lúc chờ API phản hồi để tránh người dùng click gửi nhiều lần (spam request).
  - Bắt lỗi từ API (ví dụ: "Link đã hết hạn") và hiển thị thông báo lỗi thân thiện, dễ hiểu.

- **Never (Không bao giờ):**
  - Lưu token đặt lại mật khẩu vào `localStorage` hay `sessionStorage`. Chỉ lấy token từ query parameter của URL (`?token=...`) trên thanh địa chỉ.

### UI States (Trạng thái hiển thị)

1. **Màn hình `/forgot-password`:**
    - **Loading:** Chữ nút chuyển thành "Đang gửi..." kèm spinner.
    - **Success:** Ẩn form nhập email, hiện thông báo "Đã gửi email khôi phục. Vui lòng kiểm tra hộp thư", kèm nút "Quay lại Đăng nhập".
2. **Màn hình `/reset-password?token=xxx`:**
    - **Invalid State:** Nếu URL không có `token`, lập tức hiện thông báo lỗi "Link không hợp lệ hoặc bị thiếu" và nút quay về trang chủ.
    - **Success:** Hiển thị thông báo đổi mật khẩu thành công và tự động redirect người dùng về trang `/login` sau 2-3 giây.

---

## Phần 3: Backend Specification (Yêu cầu cho BE)

### Tech Stack

- **Framework:** Java 21 & Spring Boot 3 (Spring Web, Spring Security, Spring Data JPA).

- **Database:** PostgreSQL 16 (lưu trữ thông tin người dùng `User` và token đặt lại mật khẩu).
- **Email Sending:** JavaMailSender kết hợp với Thymeleaf để tạo template email HTML.
- **Security:** `SecureRandom` để sinh token ngẫu nhiên, BCrypt để mã hóa mật khẩu.

### Project Structure (Dự kiến)

- [SecurityConfig.java](../src/main/java/com/codegym/mathclass/security/config/SecurityConfig.java): Phân quyền cho endpoint `/api/auth/forgot-password` và `/api/auth/reset-password` ở chế độ public.

- [AuthController.java](../src/main/java/com/codegym/mathclass/auth/controller/AuthController.java): Định nghĩa các REST endpoints.
- [AuthServiceImpl.java](../src/main/java/com/codegym/mathclass/auth/service/impl/AuthServiceImpl.java): Xử lý nghiệp vụ tạo token, gửi email và cập nhật mật khẩu.
- Các file DTO tương ứng: `ForgotPasswordRequest`, `ResetPasswordRequest`, `MessageResponse`.

### Security & Logic Rules

- Sử dụng **Lombok** `@Getter`, `@Setter` cho DTOs.

- Validate dữ liệu đầu vào (tầng Controller) bằng các annotation `@NotBlank`, `@Email`, `@Size`. Bắt lỗi qua Global Exception Handling (`BadRequestException`).

### Boundaries (Giới hạn & Quy tắc)

- **Always (Luôn luôn):**
  - Sử dụng `SecureRandom` để sinh token an toàn, khó đoán nhằm chống brute-force.
  - Giới hạn thời gian hiệu lực của reset token (ví dụ: 15 phút).
  - Vô hiệu hóa hoặc xóa token ngay sau khi mật khẩu được đặt lại thành công để tránh tấn công tái sử dụng (Replay Attack).
  - Mã hóa mật khẩu mới bằng `BCryptPasswordEncoder` trước khi lưu vào database.

- **Never (Không bao giờ):**
  - Gửi trực tiếp mật khẩu mới ngẫu nhiên qua email. Luôn phải gửi link chứa token để người dùng tự thiết lập mật khẩu của riêng họ.

---

## Phần 4: Integration / API Requirements (Nơi FE và BE gặp nhau)

Giao tiếp giữa Client (FE) và Server (BE) được thực hiện qua các REST API sử dụng dữ liệu định dạng JSON.

### API 1: Yêu cầu Quên mật khẩu

- **Mô tả:** FE gửi địa chỉ email của người dùng lên BE để hệ thống tạo token và gửi thư.

- **Endpoint:** `POST /api/auth/forgot-password`
- **Headers:** `Content-Type: application/json`
- **Request Body (FE gửi):**

    ```json
    {
      "email": "user@example.com"
    }
    ```

- **Response Thành công (BE trả về - HTTP 200 OK):**
  - *Lưu ý bảo mật:* BE nên trả về thành công ngay cả khi email không tồn tại trong hệ thống để tránh kẻ xấu dò quét (User Enumeration).

    ```json
    {
      "message": "Nếu email hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư của bạn."
    }
    ```

- **Response Thất bại (HTTP 400 Bad Request):** Xảy ra khi email sai định dạng hoặc bị bỏ trống.

### API 2: Đặt lại mật khẩu

- **Mô tả:** FE gửi mã token (được bóc tách từ URL) cùng với mật khẩu mới mà người dùng vừa nhập lên BE. BE tiến hành xác minh token và mã hóa mật khẩu để lưu trữ.

- **Endpoint:** `POST /api/auth/reset-password`
- **Headers:** `Content-Type: application/json`
- **Request Body (FE gửi):**

    ```json
    {
      "token": "secure-random-token-received-from-email",
      "newPassword": "newSecurePassword123"
    }
    ```

- **Response Thành công (BE trả về - HTTP 200 OK):**

    ```json
    {
      "message": "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập bằng mật khẩu mới."
    }
    ```

- **Response Thất bại (HTTP 400 Bad Request):**
  - Xảy ra khi: Token không hợp lệ, token đã hết hạn, token đã qua sử dụng, hoặc mật khẩu mới quá ngắn/quá dài (không thoả mãn điều kiện validation của BE).

    ```json
    {
      "message": "Token không hợp lệ hoặc đã hết hạn."
    }
    ```
