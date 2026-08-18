# Specification: Xác thực Cấp 2 (2FA - Google Authenticator TOTP) cho Quản trị viên (`MathClass-service`)

---

## 1. Feature Overview
- **Feature Name:** Xác thực 2 bước (Two-Factor Authentication - 2FA) bằng Google Authenticator (TOTP) và Mã dự phòng (Backup Codes) cho Quản trị viên.
- **Target Subsystem:** `MathClass-service` (Backend)
- **Target Users:** System Administrator (Role `ADMIN`)

---

## 2. Business Goal & Core Objectives

Bảo vệ an toàn tuyệt đối cho tài khoản Quản trị viên (`ADMIN`), ngăn chặn hoàn toàn rủi ro chiếm đoạt quyền quản trị khi lộ mật khẩu:

1. **Bắt buộc 100% đối với Role ADMIN**: Mọi tài khoản Admin khi đăng nhập thành công bằng Email/Password bắt buộc phải qua xác thực cấp 2 mới được cấp quyền truy cập tài nguyên Quản trị.
2. **Chuẩn TOTP (RFC 6238)**: Tương thích hoàn toàn với ứng dụng Google Authenticator, Microsoft Authenticator, Authy. Sinh Secret Key Base32 và ảnh QR Code `otpauth://totp/...`.
3. **Cơ chế Khôi phục bằng Mã dự phòng (Backup Codes)**: Khi thiết lập lần đầu, sinh 8 mã dự phòng dùng 1 lần (được băm an toàn) phòng trường hợp mất điện thoại/thiết bị.
4. **Pre-Auth JWT Architecture**: Sử dụng token tạm thời (`preAuthToken`, TTL 5 phút, scope `PRE_AUTH`) để bảo vệ các bước xác thực 2FA mà không cấp Access Token / Refresh Cookie sớm.
5. **Chống Brute-force & Replay Attack**: Giới hạn thử sai 5 lần (khóa 15 phút) và kiểm tra chống phát lại mã OTP trong chu kỳ.

---

## 3. Potential Logic Loopholes & Mitigations (5 Key Edge Cases)

### 3.1. Case 1: Lộ quyền Quản trị trước khi nhập mã 2FA
- **Vấn đề:** Nếu cấp JWT Access Token ngay sau khi đúng Password và chỉ chặn ở tầng giao diện, kẻ tấn công có thể dùng token gọi thẳng API `/api/v1/admin/**`.
- **Khắc phục:** Tuyệt đối **KHÔNG cấp JWT Cookie hay Refresh Token** ở bước đăng nhập mật khẩu cho Admin. Backend chỉ trả về `preAuthToken` với claim `scope: "PRE_AUTH"`. `AuthTokenFilter` chặn tất cả các request đến `/admin/**` hoặc `/api/v1/**` nếu token mang scope `PRE_AUTH` (chỉ cho phép gọi `/api/v1/auth/2fa/**`).

### 3.2. Case 2: Thiết lập 2FA dở dang (Orphan / Half-Setup State)
- **Vấn đề:** Admin quét QR nhưng chưa nhập mã OTP 6 số để xác nhận mà đóng trình duyệt. Nếu lưu trực tiếp `is_enabled = true` hoặc lưu đè secret chính thức, tài khoản sẽ bị kẹt không đăng nhập được.
- **Khắc phục:** Tách biệt `temp_secret_key` và `secret_key`. Khi quét QR, khóa được lưu ở `temp_secret_key` và `is_enabled = false`. Chỉ khi gọi `/2fa/setup/confirm` với mã OTP đúng, hệ thống mới chuyển sang `secret_key` và bật `is_enabled = true`.

### 3.3. Case 3: Lộ mã dự phòng (Backup Codes) khi rò rỉ Database
- **Vấn đề:** Nếu lưu mã dự phòng dưới dạng plaintext trong DB, khi database bị tấn công hoặc lộ dump, hacker có thể dùng mã backup vượt qua 2FA.
- **Khắc phục:** Mã dự phòng chỉ hiển thị **1 lần duy nhất** cho Admin lúc setup. Trong Database, toàn bộ mã backup bắt buộc phải được băm một chiều (sử dụng BCrypt hoặc SHA-256 kèm Salt).

### 3.4. Case 4: Lệch đồng hồ giữa Client và Server (Clock Drift)
- **Vấn đề:** Đồng hồ trên điện thoại người dùng có thể lệch $\pm 10 - 20$ giây so với server NTP, dẫn đến mã OTP vừa sinh ra bị coi là không hợp lệ.
- **Khắc phục:** TOTP Validator hỗ trợ dung sai thời gian **$\pm 1$ time-step (30 giây)** (tức kiểm tra bước thời gian $T-1, T, T+1$).

### 3.5. Case 5: Tấn công phát lại mã (Replay Attack)
- **Vấn đề:** Kẻ xấu bắt được mã OTP 6 số đang còn hiệu lực trong khung 30 giây và gửi lại nhiều lần.
- **Khắc phục:** Lưu timestamp hoặc hash của mã OTP vừa xác thực thành công trong Cache ngắn hạn (60s). Từ chối nếu cùng 1 mã được gửi lại trong cùng chu kỳ thời gian.

---

## 4. Functional Requirements

- **FR-1 (Admin Login Interception):** Nhận diện Admin tại endpoint `POST /api/v1/auth/login`. Kiểm tra bảng `user_two_factor_auth` để trả về `preAuthToken` kèm cờ `is2faRequired: true` và `isSetupRequired: boolean`.
- **FR-2 (TOTP Secret & QR Generation):** Khởi tạo khóa bí mật Base32 ngẫu nhiên (160-bit) và tạo ảnh QR Code dạng Data URL Base64 (`image/png`) chứa URL chuẩn `otpauth://totp/MathClass:{email}?secret={secret}&issuer=MathClass`.
- **FR-3 (Setup Confirmation & Backup Code Generation):** Xác thực mã 6 số đầu tiên, kích hoạt `is_enabled = true`, sinh danh sách 8 mã dự phòng (mỗi mã 8 ký tự `XXXX-XXXX`), lưu hash vào DB và trả về danh sách plaintext. Cấp full JWT & Refresh Token Cookie.
- **FR-4 (Login TOTP Verification):** Nhận mã OTP 6 số từ Admin, xác minh tính hợp lệ theo thuật toán HMAC-SHA1 RFC 6238. Nếu đúng, cấp full JWT & Refresh Token Cookie.
- **FR-5 (Backup Code Verification):** Hỗ trợ đăng nhập bằng mã dự phòng. Khớp hash với các mã chưa sử dụng (`is_used = false`), đánh dấu `is_used = true` và lưu `used_at = NOW()`.
- **FR-6 (Rate Limiting & Lockout):** Giới hạn tối đa 5 lần nhập sai liên tiếp. Khóa tạm thời 15 phút khi vượt ngưỡng.

---

## 5. Business Rules

- **BR-1 (Mandatory for Admin):** Bắt buộc 100% tài khoản có Role `ADMIN` phải kích hoạt và xác thực 2FA. Không cho phép Admin bỏ qua bước này để vào Dashboard.
- **BR-2 (Non-Admin Exemption):** Học sinh (`STUDENT`) và Giáo viên (`TEACHER`) không bị áp dụng 2FA trong giai đoạn này (đăng nhập bình thường nhận full token).
- **BR-3 (Single-Use Backup Codes):** Mỗi mã dự phòng chỉ có giá trị sử dụng đúng 1 lần duy nhất.
- **BR-4 (Pre-Auth Expiration):** `preAuthToken` có thời hạn tối đa 5 phút. Quá 5 phút phải đăng nhập lại từ đầu.
- **BR-5 (Atomic Confirmation):** Việc kích hoạt 2FA và lưu danh sách mã backup phải được thực hiện trong cùng một transaction `@Transactional`.
- **BR-6 (Stateless Session):** Không lưu trạng thái phiên 2FA trong HTTP Session; toàn bộ thông tin xác thực bước trung gian được mã hóa trong `preAuthToken`.

---

## 6. Data Model

> Sử dụng PostgreSQL 16. Các entity kế thừa `BaseEntity` (`id`, `created_at`, `updated_at`).

### 6.1. `UserTwoFactorAuth` — bảng `user_two_factor_auth` (Quan hệ 1 - 1 với `users`)
- **Java Class:** `com.codegym.mathclass.auth.entity.UserTwoFactorAuth extends BaseEntity`

```sql
CREATE TABLE user_two_factor_auth (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    secret_key VARCHAR(255),
    temp_secret_key VARCHAR(255),
    enabled_at TIMESTAMP,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 6.2. `UserBackupCode` — bảng `user_backup_codes` (Quan hệ 1 - N)
- **Java Class:** `com.codegym.mathclass.auth.entity.UserBackupCode extends BaseEntity`

```sql
CREATE TABLE user_backup_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(100) NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_backup_codes_user_id ON user_backup_codes(user_id);
```

---

## 7. API Contract

> Prefix: `/api/v1/auth`. Error format chuẩn: `{ "message": "...", "errorCode": "..." }`.

### 7.1. Cập nhật Đăng nhập: `POST /api/v1/auth/login`
- **Response `200 OK` (Admin chưa cài 2FA):**
```json
{
  "is2faRequired": true,
  "isSetupRequired": true,
  "preAuthToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "Tài khoản Quản trị viên bắt buộc thiết lập xác thực 2 bước."
}
```
- **Response `200 OK` (Admin đã bật 2FA):**
```json
{
  "is2faRequired": true,
  "isSetupRequired": false,
  "preAuthToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "Vui lòng nhập mã xác thực từ Google Authenticator."
}
```

### 7.2. Khởi tạo Thiết lập 2FA: `POST /api/v1/auth/2fa/setup`
- **Header:** `Authorization: Bearer <preAuthToken>`
- **Response `200 OK`:**
```json
{
  "secretKey": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "manualEntryKey": "JBSW Y3DP EHPK 3PXP"
}
```

### 7.3. Xác nhận Kích hoạt & Cấp Backup Codes: `POST /api/v1/auth/2fa/setup/confirm`
- **Header:** `Authorization: Bearer <preAuthToken>`
- **Request Body:**
```json
{
  "code": "528194"
}
```
- **Response `200 OK` (Kèm Set-Cookie JWT & Refresh Token):**
```json
{
  "userInfo": {
    "id": 1,
    "email": "admin@mathclass.com",
    "fullName": "System Admin",
    "role": "ADMIN"
  },
  "backupCodes": [
    "A1B2-C3D4",
    "E5F6-G7H8",
    "I9J0-K1L2",
    "M3N4-O5P6",
    "Q7R8-S9T0",
    "U1V2-W3X4",
    "Y5Z6-A7B8",
    "C9D0-E1F2"
  ],
  "message": "Kích hoạt xác thực 2 bước thành công!"
}
```

### 7.4. Xác thực Đăng nhập 2FA: `POST /api/v1/auth/2fa/verify`
- **Header:** `Authorization: Bearer <preAuthToken>`
- **Request Body:**
```json
{
  "code": "528194",
  "isBackupCode": false
}
```
- **Response `200 OK` (Kèm Set-Cookie JWT & Refresh Token):**
```json
{
  "id": 1,
  "email": "admin@mathclass.com",
  "fullName": "System Admin",
  "role": "ADMIN",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI..."
}
```

### 7.5. Lỗi Rate Limit (Quá 5 lần sai)
- **HTTP Status:** `429 Too Many Requests`
```json
{
  "message": "Bạn đã nhập sai mã xác thực quá 5 lần. Vui lòng thử lại sau 15 phút.",
  "errorCode": "TWO_FACTOR_RATE_LIMITED"
}
```

---

## 8. Non-Functional Requirements & Security Constraints

- **TOTP Specification:** RFC 6238 (HMAC-SHA1, 6 chữ số, chu kỳ 30 giây).
- **Time Window Tolerance:** $\pm 1$ step (chấp nhận mã trong khoảng từ $-30s$ đến $+30s$).
- **Pre-Auth Token Security:**
  - Ký bằng `jwtSecret` chung nhưng gắn claim `scope = "PRE_AUTH"`.
  - Hạn dùng cứng 5 phút (300 giây).
  - Không thể dùng token này để gọi các endpoint ngoài `/api/v1/auth/2fa/**`.
- **Database Indexing:** Index `user_id` trên bảng `user_backup_codes` để tối ưu hóa tốc độ xác thực mã dự phòng.
- **Audit Logging:** Ghi nhận sự kiện `2FA_SETUP_SUCCESS`, `2FA_LOGIN_SUCCESS`, `2FA_BACKUP_USED`, `2FA_FAILED_ATTEMPT` vào `system_logs`.

---

## 9. Acceptance Criteria Checklist

- [ ] **AC-1 (Admin Login Interception):** Admin đăng nhập mật khẩu đúng → Trả về `is2faRequired = true` và `preAuthToken`, không cấp cookie JWT chính thức.
- [ ] **AC-2 (Setup First-Time Admin):** Admin chưa cài 2FA gọi `/2fa/setup` → Nhận được mã QR Data URL và chuỗi secret key Base32.
- [ ] **AC-3 (Setup Confirmation):** Nhập đúng mã 6 số từ Google Authenticator → Lưu `secret_key`, bật `is_enabled = true`, trả về danh sách 8 mã backup code và cấp JWT cookie.
- [ ] **AC-4 (Subsequent Login Verification):** Admin đã cài 2FA nhập đúng mã 6 số tại `/2fa/verify` → Cấp JWT cookie và đăng nhập thành công vào Admin Dashboard.
- [ ] **AC-5 (Backup Code Login):** Admin nhập đúng 1 mã backup code chưa dùng → Đăng nhập thành công, mã đó bị đánh dấu `is_used = true`.
- [ ] **AC-6 (Backup Code Reuse Rejection):** Dùng lại mã backup đã sử dụng → Báo lỗi mã không hợp lệ hoặc đã dùng.
- [ ] **AC-7 (Rate Limiting):** Nhập sai mã 2FA liên tiếp 5 lần → Khóa 15 phút, trả về HTTP 429.
- [ ] **AC-8 (Non-Admin Unaffected):** Tài khoản Student/Teacher đăng nhập bình thường, nhận full JWT ngay lập tức.

---

## 10. Unit & Integration Test Cases Checklist

### 10.1. Backend Unit Tests (`TotpServiceTest.java`, `TwoFactorAuthServiceTest.java`)
- [ ] **UT-BE-01:** `generateSecretKey_shouldReturnValidBase32String()`
- [ ] **UT-BE-02:** `generateQrCodeDataUrl_shouldReturnValidPngBase64()`
- [ ] **UT-BE-03:** `verifyTotpCode_validCode_shouldReturnTrue()`
- [ ] **UT-BE-04:** `verifyTotpCode_expiredOrFutureCode_withinWindow_shouldReturnTrue()`
- [ ] **UT-BE-05:** `verifyTotpCode_invalidCode_shouldReturnFalse()`
- [ ] **UT-BE-06:** `confirmSetup_validCode_shouldActivate2faAndGenerateHashedBackupCodes()`
- [ ] **UT-BE-07:** `verifyBackupCode_validUnusedCode_shouldMarkAsUsedAndReturnTrue()`
- [ ] **UT-BE-08:** `verifyBackupCode_alreadyUsedCode_shouldReturnFalse()`
- [ ] **UT-BE-09:** `verify2fa_exceedMaxFailedAttempts_shouldLockUserAndThrowTooManyRequestsException()`

### 10.2. Backend Integration Tests (`AuthController2faIntegrationTest.java`)
- [ ] **IT-BE-01:** `POST /api/v1/auth/login` với role `ADMIN` → Trả về `preAuthToken` và `is2faRequired: true`.
- [ ] **IT-BE-02:** Gọi `/api/v1/admin/users` với `preAuthToken` → Nhận `403 Forbidden` (chặn token chưa qua 2FA).
- [ ] **IT-BE-03:** Luồng hoàn chỉnh: `login` → `2fa/setup` → `2fa/setup/confirm` → Nhận được JWT Cookie hợp lệ.
- [ ] **IT-BE-04:** Luồng đăng nhập định kỳ: `login` → `2fa/verify` với mã TOTP đúng → Đăng nhập thành công.
- [ ] **IT-BE-05:** `POST /api/v1/auth/2fa/verify` sai 5 lần → Nhận `429 Too Many Requests`.

---

## 11. Implementation Checklist

- [ ] Thêm dependencies vào `build.gradle`:
  - `com.warrenstrange:googleauth:1.5.0` (hoặc custom RFC 6238 TOTP generator)
  - `com.google.zxing:core:3.5.3` & `com.google.zxing:javase:3.5.3` (sinh QR Code)
- [ ] Tạo Entity + Repository: `UserTwoFactorAuth`, `UserBackupCode`.
- [ ] Xây dựng Service `TotpService` (sinh secret, sinh QR Data URL, verify mã 6 số theo time-step).
- [ ] Cập nhật `JwtUtils` để hỗ trợ tạo & validate `preAuthToken` với claim `scope: PRE_AUTH`.
- [ ] Xây dựng DTOs: `TwoFactorSetupResponse`, `TwoFactorVerifyRequest`, `TwoFactorConfirmRequest`, `TwoFactorLoginResponse`.
- [ ] Xây dựng Service `TwoFactorAuthService` và `TwoFactorAuthServiceImpl`.
- [ ] Xây dựng REST Controller `TwoFactorAuthController` (các endpoint `/api/v1/auth/2fa/*`).
- [ ] Cập nhật `AuthServiceImpl.authenticateUser` để nhận diện Admin và chặn cấp cookie nếu chưa qua 2FA.
- [ ] Cập nhật `AuthTokenFilter` để từ chối token mang claim `PRE_AUTH` khi truy cập tài nguyên chính thức.
- [ ] Viết Unit Tests và Integration Tests đạt 100% checklist mục 10.
