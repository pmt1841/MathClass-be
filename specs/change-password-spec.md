# Specification: Change Password Feature (`MathClass-service` & `MathClass-fe`)

## 1. Executive Summary & Objectives

Tính năng **Đổi Mật Khẩu (Change Password)** cho phép người dùng đang đăng nhập tự đổi mật khẩu tài khoản cá nhân trực tiếp từ trang **Cài Đặt (`/settings`)**.
Tính năng tuân thủ các quy tắc bảo mật:
- Yêu cầu mật khẩu hiện tại, mật khẩu mới và xác nhận mật khẩu mới.
- Mật khẩu mới có độ dài tối thiểu 6 ký tự.
- Mật khẩu mới không trùng với mật khẩu hiện tại.
- **Không trùng với 3 mật khẩu gần nhất** của người dùng (lưu vết tại bảng `password_histories`).
- Đổi mật khẩu thành công sẽ **tự động đăng xuất (logout)** và hủy session/token để buộc đăng nhập lại.

---

## 2. Acceptance Criteria Checklist (AC)

- [ ] **AC-01:** Người dùng đã đăng nhập có thể truy cập khối "Đổi mật khẩu" tại trang `/settings` và nhập Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới.
- [ ] **AC-02:** Mật khẩu mới phải có tối thiểu 6 ký tự. Nếu ít hơn 6 ký tự, hiển thị thông báo lỗi validation.
- [ ] **AC-03:** Mật khẩu mới và Mật khẩu xác nhận phải trùng khớp hoàn toàn. Nếu không khớp, trả về lỗi "Mật khẩu xác nhận không trùng khớp".
- [ ] **AC-04:** Mật khẩu hiện tại phải được xác thực chính xác. Nếu nhập sai mật khẩu hiện tại, trả về lỗi "Mật khẩu hiện tại không đúng".
- [ ] **AC-05:** Mật khẩu mới không được trùng với mật khẩu hiện tại. Nếu trùng, trả về lỗi "Mật khẩu mới không được trùng với mật khẩu hiện tại".
- [ ] **AC-06:** Mật khẩu mới không được trùng với bất kỳ mật khẩu nào trong 3 mật khẩu gần nhất của người dùng. Nếu trùng, trả về lỗi "Mật khẩu mới không được trùng với 3 mật khẩu gần nhất".
- [ ] **AC-07:** Khi đổi mật khẩu thành công:
  - Mật khẩu mới được mã hóa (Bcrypt) và lưu vào `users`.
  - Mật khẩu cũ được tự động ghi vết vào bảng `password_histories`.
  - Trả về kết quả `200 OK`.
  - Frontend hiển thị Toast thành công, tự động thực hiện đăng xuất (`logout()`) và chuyển hướng về `/login`.

---

## 3. Backend Specifications (`MathClass-service`)

### 3.1. Database Schema (`password_histories`)
Tạo bảng mới `password_histories` trong PostgreSQL:

| Attribute | Field Name | Data Type | Constraints / Details |
| :--- | :--- | :--- | :--- |
| Primary Key | `id` | `BIGINT` | Auto-increment Primary Key (từ `BaseEntity`) |
| Foreign Key | `user_id` | `BIGINT` | Liên kết `users(id)` (ON DELETE CASCADE) |
| Hashed Password | `hashed_password` | `VARCHAR(255)` | Chuỗi mã hóa Bcrypt của mật khẩu cũ |
| Timestamp | `createdAt` | `LocalDateTime` | Thời điểm ghi nhận (từ `BaseEntity`) |

### 3.2. Entity Class (`PasswordHistory`)
- **Package:** `com.codegym.mathclass.user.entity`
- **Class:** `PasswordHistory` extends `BaseEntity`
- **Relationships:** `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)`

### 3.3. Repository (`PasswordHistoryRepository`)
- **Package:** `com.codegym.mathclass.user.repository`
- **Interface:** `PasswordHistoryRepository` extends `JpaRepository<PasswordHistory, Long>`
- **Method:** `List<PasswordHistory> findTop3ByUserIdOrderByCreatedAtDesc(Long userId)`

### 3.4. DTO Specifications
- **File:** `com.codegym.mathclass.user.dto.request.ChangePasswordRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    private String currentPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới phải có tối thiểu 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
    private String confirmPassword;
}
```

### 3.5. Controller Endpoint
- **File:** `com.codegym.mathclass.user.controller.UserController`
- **Endpoint:** `PUT /api/v1/users/me/password`
- **Annotations:** `@Operation(summary = "Đổi mật khẩu tài khoản cá nhân")`
- **Method Signature:**
```java
@PutMapping("/me/password")
public ResponseEntity<ApiResponse<String>> changePassword(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(userDetails.getId(), request);
    return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại."));
}
```

### 3.6. Service Business Logic (`UserServiceImpl.changePassword`)
1. **Kiểm tra mật khẩu xác nhận:** `newPassword.equals(confirmPassword)`, nếu không khớp throw `BadRequestException("Mật khẩu xác nhận không trùng khớp")`.
2. **Kiểm tra mật khẩu hiện tại:** `passwordEncoder.matches(currentPassword, user.getPassword())`, nếu sai throw `BadRequestException("Mật khẩu hiện tại không chính xác")`.
3. **Kiểm tra mật khẩu mới trùng mật khẩu hiện tại:** `passwordEncoder.matches(newPassword, user.getPassword())`, nếu trùng throw `BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại")`.
4. **Kiểm tra 3 mật khẩu gần nhất:** Lấy danh sách `PasswordHistory` top 3 gần nhất của user. Nếu `passwordEncoder.matches(newPassword, history.getHashedPassword())` cho bất kỳ lịch sử nào -> throw `BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất")`.
5. **Lưu lịch sử mật khẩu cũ:** Tạo `PasswordHistory(user, user.getPassword())` và save vào DB.
6. **Cập nhật mật khẩu mới:** `user.setPassword(passwordEncoder.encode(newPassword))` và save `user`.

---

## 4. Frontend Specifications (`MathClass-fe`)

### 4.1. API Service Method (`userService.ts`)
- **Method:** `changePassword(data: ChangePasswordRequest): Promise<ApiResponse<string>>`
- **Call:** `axiosClient.put('/users/me/password', data)`

### 4.2. Component Architecture (`ChangePasswordCard.tsx`)
- **File:** `components/settings/ChangePasswordCard.tsx`
- **UI Element:** Card chứa các trường:
  - Mật khẩu hiện tại (`Input type="password"`, icon ẩn/hiện)
  - Mật khẩu mới (`Input type="password"`, icon ẩn/hiện)
  - Xác nhận mật khẩu mới (`Input type="password"`, icon ẩn/hiện)
  - Nút bấm "Đổi mật khẩu" (có hiệu ứng `loading`/`animate-spin`)
- **Integration:** Đặt dưới Card "Thông báo qua Email" trong file `app/(dashboard)/settings/page.tsx`.

### 4.3. UX Flow & Logout Handling
- Sử dụng `useMutation` từ TanStack Query.
- Khi API trả về `200 OK`:
  - Hiển thị Toast xanh: `"Đổi mật khẩu thành công. Đang đăng xuất..."`.
  - Thực thi `logout()` từ `useAuth()`.
  - Chuyển hướng router về `/login`.
- Khi API lỗi:
  - Hiển thị Toast đỏ với câu thông báo lỗi chi tiết từ backend (ví dụ: `"Mật khẩu hiện tại không đúng"` hoặc `"Mật khẩu mới không được trùng với 3 mật khẩu gần nhất"`).

---

## 5. Unit Test Cases Checklist

### 5.1. Backend (`MathClass-service`) Test Cases (`UserServiceTest` & `UserControllerTest`)

#### 🟢 Happy Path
- [ ] **UT-BE-01:** `changePassword_success_validCredentials` - Đổi mật khẩu thành công khi nhập đúng mật khẩu hiện tại, mật khẩu mới $\ge 6$ ký tự, không trùng mật khẩu hiện tại và không trùng 3 mật khẩu gần nhất.
- [ ] **UT-BE-02:** `changePassword_success_savesPasswordHistory` - Đảm bảo mật khẩu cũ được lưu thành công vào bảng `password_histories` sau khi đổi thành công.
- [ ] **UT-BE-03:** `changePassword_success_allowsPasswordOlderThan3History` - Cho phép đổi thành công nếu mật khẩu mới trùng với mật khẩu thứ 4 trở về trước (nằm ngoài Top 3 gần nhất).

#### 🔴 Edge Cases & Exceptions
- [ ] **UT-BE-04:** `changePassword_fail_incorrectCurrentPassword` - Ném `BadRequestException("Mật khẩu hiện tại không đúng")` khi nhập sai mật khẩu hiện tại.
- [ ] **UT-BE-05:** `changePassword_fail_confirmPasswordMismatch` - Ném `BadRequestException("Mật khẩu xác nhận không trùng khớp")` khi mật khẩu xác nhận không khớp.
- [ ] **UT-BE-06:** `changePassword_fail_sameAsCurrentPassword` - Ném `BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại")` khi mật khẩu mới trùng mật khẩu hiện tại.
- [ ] **UT-BE-07:** `changePassword_fail_matches1stPreviousPassword` - Ném `BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất")` khi trùng mật khẩu cũ gần nhất.
- [ ] **UT-BE-08:** `changePassword_fail_matches2ndPreviousPassword` - Ném `BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất")` khi trùng mật khẩu cũ thứ 2.
- [ ] **UT-BE-09:** `changePassword_fail_matches3rdPreviousPassword` - Ném `BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất")` me trùng mật khẩu cũ thứ 3.
- [ ] **UT-BE-10:** `changePassword_fail_invalidDtoValidation` - Controller trả về `400 Bad Request` khi `newPassword` ngắn hơn 6 ký tự hoặc có trường bị trống (`@Valid`).
- [ ] **UT-BE-11:** `changePassword_fail_unauthenticatedUser` - Controller trả về `401 Unauthorized` khi người dùng chưa đăng nhập / token không hợp lệ.
- [ ] **UT-BE-12:** `changePassword_fail_userIsGoogleProvider` - Ném `BadRequestException` khi tài khoản người dùng đăng nhập bằng Google Provider.

### 5.2. Frontend (`MathClass-fe`) Test Cases (`ChangePasswordCard.test.tsx`)

#### 🟢 Happy Path
- [ ] **UT-FE-01:** Component render đầy đủ 3 ô nhập (Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới) và nút Submit "Đổi mật khẩu".
- [ ] **UT-FE-02:** Nút icon con mắt toggle ẩn/hiện mật khẩu hoạt động chính xác (`type="password"` $\leftrightarrow$ `type="text"`).
- [ ] **UT-FE-03:** Đổi mật khẩu thành công: Gọi API `userService.changePassword`, hiển thị toast thành công, gọi `logout()` và chuyển hướng về `/login`.

#### 🔴 Edge Cases
- [ ] **UT-FE-04:** Báo lỗi trực tiếp trên giao diện khi người dùng nhập mật khẩu xác nhận không trùng khớp trước khi nhấn nút Submit.
- [ ] **UT-FE-05:** Disable nút Submit và hiển thị trạng thái loading spinner khi mutation đang trong trạng thái `isPending`.
- [ ] **UT-FE-06:** Hiển thị toast lỗi màu đỏ chứa nội dung lỗi phản hồi từ phía Backend khi gặp lỗi 400 Bad Request.

---

## 6. Verification Strategy

1. **Backend Verification:**
   - Command: `./gradlew compileJava`
   - Command: `./gradlew test --tests com.codegym.mathclass.user.*`
2. **Frontend Verification:**
   - Command: `npx tsc --noEmit`
   - Command: `npm run lint`
   - Test thủ công giao diện trên trình duyệt tại trang `/settings`.
