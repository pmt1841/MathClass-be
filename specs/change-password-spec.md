# Specification: Change Password & Set Initial Password Feature (`MathClass-service` & `MathClass-fe`)

## 1. Executive Summary & Objectives

Tính năng hỗ trợ hai luồng quản lý mật khẩu chính dành cho người dùng đã đăng nhập:
1. **Luồng Đổi Mật Khẩu (Change Password):** Dành cho tài khoản `LOCAL` hoặc tài khoản `GOOGLE` đã khởi tạo mật khẩu local (`hasPassword == true`).
   - Yêu cầu: Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới.
   - Hỗ trợ đường dẫn phụ **"Quên mật khẩu?"** bên dưới ô Mật khẩu hiện tại dẫn tới luồng `/forgot-password`.
2. **Luồng Thiết Lập Mật Khẩu Lần Đầu (Set Password):** Dành cho tài khoản `GOOGLE` chưa từng tạo mật khẩu riêng (`hasPassword == false`).
   - Yêu cầu: Bấm *"Gửi mã xác thực về email"* để nhận OTP 6 số $\rightarrow$ Nhập Mã OTP + Mật khẩu mới + Xác nhận mật khẩu mới.
3. **Quy chuẩn Bảo mật đi kèm:**
   - **Security Alert Mail:** Tự động gửi email cảnh báo bảo mật tới Gmail người dùng ngay sau khi đổi/thiết lập mật khẩu thành công.
   - **Revoke Tokens / Session Cleanup:** Đăng xuất tự động và hủy các phiên đăng nhập khác.
   - **Lịch sử mật khẩu:** Mật khẩu mới không trùng mật khẩu hiện tại và 3 mật khẩu gần nhất.

---

## 2. Acceptance Criteria Checklist (AC)

- [ ] **AC-01:** `UserResponse` trả về trường `hasPassword: boolean` phản ánh trạng thái tài khoản đã có mật khẩu hay chưa.
- [ ] **AC-02 (Luồng Đổi mật khẩu):** Với tài khoản `hasPassword == true`, hiển thị Form 3 ô nhập: Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới.
- [ ] **AC-03 (Liên kết Quên mật khẩu):** Bên dưới ô Mật khẩu hiện tại có nút/link nhỏ "Quên mật khẩu?" chuyển hướng đến `/forgot-password`.
- [ ] **AC-04 (Luồng Thiết lập mật khẩu):** Với tài khoản Google `hasPassword == false`, giao diện chuyển sang tiêu đề *"Thiết lập mật khẩu đăng nhập (Set Password)"*, không yêu cầu nhập mật khẩu hiện tại.
- [ ] **AC-05 (Gửi OTP Email):** Người dùng bấm *"Gửi mã xác thực về email"* $\rightarrow$ Backend gửi OTP 6 số có hiệu lực 5 phút về email.
- [ ] **AC-06 (Xác thực OTP & Lưu mật khẩu mới):** Nhập đúng OTP + Mật khẩu mới trùng Mật khẩu xác nhận $\rightarrow$ Lưu băm Bcrypt mật khẩu mới vào DB và cập nhật `hasPassword = true`.
- [ ] **AC-07 (Lịch sử mật khẩu):** Mật khẩu mới không được trùng với mật khẩu hiện tại hoặc 3 mật khẩu cũ trong `password_histories`. Nếu trùng $\rightarrow$ Hiển thị Center Warning Modal.
- [ ] **AC-08 (Security Alert Mail):** Hệ thống gửi email tự động với nội dung: *"Mật khẩu của bạn vừa được thay đổi thành công vào lúc HH:mm ngày dd/MM/yyyy. Nếu không phải bạn thực hiện, vui lòng liên hệ ngay với quản trị viên."*
- [ ] **AC-09 (Revoke Session & Auto Logout):** Đổi/Thiết lập mật khẩu thành công sẽ hủy toàn bộ phiên làm việc của user, xóa token client và chuyển hướng về `/login`.

---

## 3. Backend Specifications (`MathClass-service`)

### 3.1. UserResponse DTO Enhancement
- **File:** `com.codegym.mathclass.user.dto.response.UserResponse`
- **Field:** `private boolean hasPassword;`
- **Logic Mapping:** `user.getPassword() != null && !user.getPassword().trim().isEmpty()`

### 3.2. Set Password DTO Specifications
- **File:** `com.codegym.mathclass.user.dto.request.SetPasswordRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetPasswordRequest {
    @NotBlank(message = "Mã OTP không được để trống")
    @Size(min = 6, max = 6, message = "Mã OTP phải gồm 6 chữ số")
    private String otpCode;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới phải có tối thiểu 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
    private String confirmPassword;
}
```

### 3.3. Controller API Endpoints (`UserController.java`)
- **PUT `/api/v1/users/me/password`**: Đổi mật khẩu dành cho user đã có mật khẩu (`ChangePasswordRequest`).
- **POST `/api/v1/users/me/set-password/send-otp`**: Gửi OTP 6 số về Gmail của user đang đăng nhập để chuẩn bị thiết lập mật khẩu lần đầu.
- **PUT `/api/v1/users/me/set-password`**: Xác thực OTP & lưu mật khẩu mới cho user chưa có mật khẩu (`SetPasswordRequest`).

### 3.4. Email Notification (`EmailService.java`)
- Bổ sung phương thức `sendSecurityAlertEmail(String toEmail, String fullName, LocalDateTime changeTime)` gửi HTML email cảnh báo bảo mật khi mật khẩu thay đổi.

---

## 4. Frontend Specifications (`MathClass-fe`)

### 4.1. Component Architecture
- **`ChangePasswordCard.tsx`**: Render khi `user.hasPassword === true`.
  - Ô nhập: Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới.
  - Phía dưới ô Mật khẩu hiện tại: Thêm link `<Link href="/forgot-password" className="text-xs text-indigo-600 hover:underline">Quên mật khẩu?</Link>`.
- **`SetPasswordCard.tsx`**: Render khi `user.hasPassword === false`.
  - Nút bấm *"Gửi mã xác thực về email"* kèm đếm ngược 60s resend.
  - Ô nhập: Mã OTP (6 số), Mật khẩu mới, Xác nhận mật khẩu mới.

---

## 5. Unit Test Cases Checklist

### Backend (`MathClass-service`)
- [ ] **UT-BE-01:** `changePassword_success` - Đổi mật khẩu thành công khi nhập đúng mật khẩu hiện tại và gửi email cảnh báo bảo mật.
- [ ] **UT-BE-02:** `sendSetPasswordOtp_success` - Gửi OTP 6 số thành công tới email user chưa có mật khẩu.
- [ ] **UT-BE-03:** `setPassword_success` - Nhập đúng OTP 6 số, thiết lập mật khẩu mới thành công và chuyển `hasPassword = true`.
- [ ] **UT-BE-04:** `setPassword_fail_invalidOtp` - Báo lỗi khi nhập sai OTP hoặc OTP đã quá hạn 5 phút.
- [ ] **UT-BE-05:** `changePassword_fail_matchesHistory` - Hiển thị cảnh báo khi trùng 3 mật khẩu gần nhất.

### Frontend (`MathClass-fe`)
- [ ] **UT-FE-01:** Thể hiện đúng form Đổi mật khẩu (có link Quên mật khẩu) khi `hasPassword == true`.
- [ ] **UT-FE-02:** Thể hiện đúng form Thiết lập mật khẩu (gửi OTP + nhập OTP) khi `hasPassword == false`.
- [ ] **UT-FE-03:** Hiển thị Center Warning Modal khi người dùng nhập mật khẩu trùng lịch sử.
