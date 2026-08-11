# Đặc Tả Kỹ Thuật: Khóa & Mở Khóa Tài Khoản Người Dùng Kèm Lý Do & Gửi Email Thông Báo

## 1. Tổng Quan & Mục Tiêu

### 1.1. Mục tiêu
1. **Khóa tài khoản (`isActive = false`):** Bắt buộc Quản trị viên (Admin) cung cấp **lý do khóa**. Hệ thống ghi nhận lý do vào Database, vô hiệu hóa phiên truy cập lập tức, và tự động gửi **Email thông báo bất đồng bộ** tới người dùng bị khóa.
2. **Mở khóa tài khoản (`isActive = true`):** Cho phép Admin tùy chọn chọn Preset hoặc nhập lý do mở khóa (**không bắt buộc**, có thể để trống). Sau khi mở khóa, hệ thống tự động gửi **Email thông báo bất đồng bộ** (đính kèm lý do nếu Admin nhập, hoặc thông báo đơn giản nếu để trống).
3. **Hiển thị Lý do Bị Khóa cho Người Dùng (User Experience):** Khi người dùng bị khóa truy cập hệ thống hoặc cố tình đăng nhập, hệ thống trả về thông tin `lockReason` và `lockedAt` trong JSON error response (mã 403 Forbidden / 400 Bad Request). Trình duyệt hiển thị Modal Cảnh Báo kèm đầy đủ lý do bị khóa và thời điểm bị khóa trực quan ngay trên trang Đăng nhập.

### 1.2. Quyền truy cập
- **Yêu cầu phân quyền:** Chỉ tài khoản có Role `ADMIN` hoặc `SYSTEM_ADMIN` (được bảo vệ bởi `@PreAuthorize("hasRole('ADMIN')")`).

---

## 2. Nhật Ký Quyết Định (Decision Log)

| Quyết định | Phương án đã chọn | Lựa chọn thay thế | Lý do chọn |
| :--- | :--- | :--- | :--- |
| **Bắt buộc lý do Khóa** | Khóa tài khoản (`isActive = false`) bắt buộc chọn/nhập lý do (min 5, max 500 ký tự). | Cho phép để trống lý do khóa. | Lý do khóa liên quan trực tiếp đến vi phạm của người dùng và cần thông báo rõ ràng. |
| **Tùy chọn lý do Mở khóa** | Mở khóa tài khoản (`isActive = true`) cho phép chọn Preset/nhập lý do hoặc để trống. | Bắt buộc nhập lý do mở khóa / Không cho phép nhập lý do mở khóa. | Linh hoạt cho Admin khi mở khóa do hết hạn tạm khóa hoặc đã giải quyết xong vi phạm. |
| **Lý do mặc định (Presets)** | Gợi ý 5–6 Presets có sẵn cho cả Khóa & Mở khóa + Lựa chọn "Khác" tự nhập. | Chỉ nhập tự do / Chỉ chọn cố định. | Thao tác nhanh với các lý do phổ biến, vừa đảm bảo tính linh hoạt cho trường hợp đặc biệt. |
| **Lưu vết Lịch sử & Audit** | Lưu thông tin khóa gần nhất ở `User` entity + Lưu lịch sử đầy đủ ở `user_lock_histories`. Dùng `lockedBy` / `performedBy` (String). | Chỉ lưu trên User entity / Dùng Foreign Key `locked_by_id`. | Đảm bảo tính bất đồng vết (Snapshots), bảo toàn dữ liệu khi Admin bị xóa hoặc nghỉ việc. |
| **Tách Enum `LockActionType`** | Tạo file enum độc lập `LockActionType.java` (`LOCK`, `UNLOCK`). | Khai báo inner enum trong `UserLockHistory`. | Đảm bảo Clean Code, tái sử dụng enum ở DTO/Event/Service và khớp chuẩn thiết kế codebase. |
| **Gửi Email thông báo bất đồng bộ** | Phát Spring Event (`UserAccountLockedEvent` & `UserAccountUnlockedEvent`) xử lý **bất đồng bộ (`@Async`)**. | Gửi email đồng bộ trong Transaction chính. | Tránh làm tăng độ trễ (latency) của API response. Lỗi gửi mail không gây rollback hành động của Admin. |
| **Hiển thị Lý do bị khóa cho User** | Trả về `lockReason` và `lockedAt` trong JSON Response lỗi 403 / 400. Frontend lưu và render trực tiếp trong `AccountLockedModal.tsx`. | Chỉ báo lỗi chung "Tài khoản bị khóa" và bắt user gọi API khác tra cứu. | Tăng trải nghiệm người dùng (UX), giúp người dùng bị khóa hiểu ngay nguyên nhân mà không bị bỡ ngỡ. |

---

## 3. ⚠️ 5 Lỗi / Lỗ Hổng Logic Tiềm Ẩn & Giải Pháp Xử Lý

### 🔴 Lỗi 1: Access Token (JWT) vẫn còn hiệu lực sau khi khóa tài khoản
- **Nguy cơ:** Việc chỉ xóa `RefreshToken` trong DB khiến người dùng bị khóa vẫn có thể tiếp tục sử dụng `Access Token` cũ (thường sống từ 15–60 phút) để gọi các API hệ thống cho đến khi token hết hạn.
- **Giải pháp xử lý:**
  1. Trong `AuthTokenFilter`, kiểm tra trạng thái `user.isActive()` trực tiếp từ DB/CustomUserDetails cho mỗi request.
  2. Nếu `user.isActive() == false`, lập tức trả về `403 Forbidden` kèm JSON Body chứa `code: "ACCOUNT_LOCKED"`, `message`, `lockReason`, `lockedAt` và xóa sạch Cookie phiên làm việc.

### 🔴 Lỗi 2: Lỗi Race Condition / Khóa hoặc Mở khóa trùng lặp
- **Nguy cơ:** Hai Admin cùng bấm khóa/mở khóa 1 tài khoản đồng thời, hoặc Admin bấm khóa một tài khoản vốn đã bị khóa từ trước.
- **Giải pháp xử lý:**
  1. Kiểm tra trạng thái hiện tại trước khi cập nhật: Nếu `user.isActive() == targetIsActive`, ném `BadRequestException` với thông điệp thích hợp ("Tài khoản này hiện đang ở trạng thái bị khóa/hoạt động").

### 🔴 Lỗi 3: Nguy cơ HTML Injection / XSS qua trường Lý do (`reason`)
- **Nguy cơ:** Truyền chuỗi chứa mã độc HTML/JS vào `reason` (ví dụ: `<script>alert('xss')</script>`). Khi render vào Email HTML hoặc Admin UI gây lỗ hổng XSS.
- **Giải pháp xử lý:**
  1. Thực hiện HTML Sanitization / Escaping đối với chuỗi `reason` (chuyển `<` -> `&lt;`, `>` -> `&gt;`, `&` -> `&amp;`,...) trước khi đưa vào Database, Email HTML Template và DTO response.
  2. Giới hạn độ dài ký tự (`@Size(max = 500)`).

### 🔴 Lỗi 4: Lỗi gửi Email im lặng (Silent Email Failure)
- **Nguy cơ:** Do gửi mail bất đồng bộ (`@Async`), nếu server SMTP bị timeout, API vẫn trả về `200 OK` báo Admin *"Cập nhật thành công"*, nhưng người dùng không nhận được mail.
- **Giải pháp xử lý:**
  1. Trong `@Async Event Listener`, catch Exception và ghi nhận log audit hệ thống (`SystemLogService`) với mã lỗi `EMAIL_SEND_FAILED`.

### 🔴 Lỗi 5: Mất lịch sử vi phạm khi Mở khóa tài khoản (Unlock Account)
- **Nguy cơ:** Khi Mở khóa (`isActive = true`), nếu set `lockReason = null`, `lockedAt = null` trực tiếp trên bảng `users` mà không lưu vết, lịch sử vi phạm cũ bị xóa sạch.
- **Giải pháp xử lý:**
  1. Mọi thao tác Khóa và Mở khóa đều ghi thêm 1 bản ghi mới vào bảng `user_lock_histories` với `actionType` tương ứng (`LOCK` hoặc `UNLOCK`) và `reason`.
  2. Khi Mở khóa, reset `lockReason = null`, `lockedAt = null`, `lockedBy = null` trên Entity `User` chính để biểu thị trạng thái sạch hiện tại.

---

## 4. Mô Hình Dữ Liệu (Data Model)

### 4.1. Entity `User` (`com.codegym.mathclass.user.entity.User`)
```java
@Column(name = "lock_reason", columnDefinition = "TEXT")
private String lockReason;

@Column(name = "locked_at")
private LocalDateTime lockedAt;

@Column(name = "locked_by")
private String lockedBy;
```

### 4.2. Enum `LockActionType` (`com.codegym.mathclass.user.entity.LockActionType`)
```java
package com.codegym.mathclass.user.entity;

public enum LockActionType {
    LOCK,
    UNLOCK
}
```

### 4.3. Entity `UserLockHistory` (`com.codegym.mathclass.user.entity.UserLockHistory`)
```java
@Entity
@Table(name = "user_lock_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLockHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private LockActionType actionType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "performed_by", nullable = false)
    private String performedBy; // Email Admin thực hiện
}
```

### 4.4. Migration SQL (PostgreSQL)
```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_reason TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_by VARCHAR(255);

CREATE TABLE IF NOT EXISTS user_lock_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    action_type VARCHAR(20) NOT NULL,
    reason TEXT,
    performed_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_lock_histories_user_id ON user_lock_histories(user_id);
```

---

## 5. Chi Tiết API Contract

### 5.1. Endpoint Cập nhật Trạng thái Tài khoản
- **HTTP Method:** `PATCH`
- **URL Path:** `/api/admin/users/{id}/status`
- **Headers:** `Authorization: Bearer <JWT_ADMIN_TOKEN>`

#### Request Body Khóa Tài Khoản (`UpdateUserStatusRequest`):
```json
{
  "isActive": false,
  "reason": "Nghi vấn gian lận bài tập tự luận trong kỳ thi giữa kỳ"
}
```

#### Request Body Mở Khóa Tài Khoản:
```json
{
  "isActive": true,
  "reason": "Đã xác minh tài khoản an toàn và làm rõ nhầm lẫn"
}
```
*(Trường `reason` khi `isActive == true` có thể để trống hoặc không gửi)*.

#### Ràng buộc Validation:
- `isActive` (Boolean): `@NotNull(message = "Trạng thái không được để trống")`
- `reason` (String):
  - Khi `isActive == false`: **Bắt buộc**, độ dài từ 5 đến 500 ký tự.
  - Khi `isActive == true`: Không bắt buộc (cho phép null hoặc rỗng, tối đa 500 ký tự).

#### Response Status Codes:
- `200 OK`: Cập nhật trạng thái thành công.
- `400 Bad Request`: Lỗi validation (thiếu lý do khi khóa, lý do quá ngắn, hoặc tài khoản đã ở trạng thái mục tiêu).
- `403 Forbidden`: Admin tự khóa chính mình / Không đủ quyền.
- `404 Not Found`: Không tìm thấy `userId`.

#### Response Body Thành Công (`MessageResponse`):
```json
{
  "message": "Trạng thái tài khoản đã được cập nhật thành công."
}
```

#### JSON Response Lỗi Khi Tài Khoản Bị Khóa (`403 Forbidden` từ `AuthTokenFilter`):
```json
{
  "code": "ACCOUNT_LOCKED",
  "message": "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.",
  "lockReason": "Nghi vấn gian lận bài tập tự luận trong kỳ thi giữa kỳ",
  "lockedAt": "2026-08-11T08:30:00"
}
```

---

## 6. Logic Backend & Event-Driven Email

### 6.1. Luồng xử lý chi tiết trong `AdminUserServiceImpl`
1. Validate `userId`: Kiểm tra sự tồn tại trong CSDL.
2. Validate Admin: Chặn Admin tự khóa tài khoản của chính mình (`currentAdminEmail`).
3. Kiểm tra trạng thái hiện tại (Race condition protection): Nếu `user.isActive() == targetIsActive`, ném `BadRequestException`.
4. Cập nhật `User` Entity:
   - Nếu `isActive == false`: Gán `lockReason = sanitizeReason(request.getReason())`, `lockedAt = LocalDateTime.now()`, `lockedBy = currentAdminEmail`.
   - Nếu `isActive == true`: Đặt `lockReason = null`, `lockedAt = null`, `lockedBy = null`.
5. Ghi nhật ký lịch sử: Lưu bản ghi `UserLockHistory` mới với `actionType = LOCK` hoặc `UNLOCK` và `reason` (đã sanitize).
6. Vô hiệu hóa phiên hoạt động: Xóa toàn bộ Refresh Token của User trong DB.
7. Phát Spring Event:
   - Nếu `isActive == false`: Bắn `UserAccountLockedEvent`.
   - Nếu `isActive == true`: Bắn `UserAccountUnlockedEvent`.
8. Ghi log hệ thống `SystemLogService` và trả về thông báo thành công.

### 6.2. Struct Event Classes
```java
// Event Khóa tài khoản
public record UserAccountLockedEvent(
    Long userId,
    String userEmail,
    String fullName,
    String reason,
    LocalDateTime lockedAt,
    String performedBy
) {}

// Event Mở khóa tài khoản
public record UserAccountUnlockedEvent(
    Long userId,
    String userEmail,
    String fullName,
    String unlockReason,
    LocalDateTime unlockedAt,
    String performedBy
) {}
```

### 6.3. Event Listener Bất Đồng Bộ (`UserAccountEventListener.java`)
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccountEventListener {

    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Async
    @EventListener
    public void handleUserAccountLocked(UserAccountLockedEvent event) {
        try {
            emailService.sendAccountLockedEmail(
                event.userEmail(),
                event.fullName(),
                event.reason(),
                event.lockedAt()
            );
            log.info("Đã gửi email thông báo khóa tài khoản cho: {}", event.userEmail());
        } catch (Exception e) {
            log.error("Lỗi gửi email khóa tài khoản cho {}: {}", event.userEmail(), e.getMessage(), e);
            systemLogService.logError("SYSTEM", "EMAIL_SEND_FAILED: " + event.userEmail(), null);
        }
    }

    @Async
    @EventListener
    public void handleUserAccountUnlocked(UserAccountUnlockedEvent event) {
        try {
            emailService.sendAccountUnlockedEmail(
                event.userEmail(),
                event.fullName(),
                event.unlockReason(),
                event.unlockedAt()
            );
            log.info("Đã gửi email thông báo mở khóa tài khoản cho: {}", event.userEmail());
        } catch (Exception e) {
            log.error("Lỗi gửi email mở khóa tài khoản cho {}: {}", event.userEmail(), e.getMessage(), e);
            systemLogService.logError("SYSTEM", "EMAIL_SEND_FAILED: " + event.userEmail(), null);
        }
    }
}
```

---

## 7. Giao Diện Người Dùng & UX Flow (MathClass-fe)

### 7.1. Presets Lý do Khóa Tài Khoản (`LockUserModal.tsx`)
1. `Vi phạm tiêu chuẩn cộng đồng / Ngôn từ không phù hợp`
2. `Nghi vấn gian lận bài tập / Bài thi`
3. `Chia sẻ / Sử dụng chung tài khoản trái phép`
4. `Tài khoản vi phạm an toàn & bảo mật`
5. `Yêu cầu tạm khóa từ người dùng / Phụ huynh`
6. `Khác (Tự nhập lý do)` *(Bắt buộc nhập min 5 ký tự)*

### 7.2. Presets Lý do Mở Khóa Tài Khoản (`UnlockUserModal.tsx`)
1. `Đã xác minh tài khoản an toàn`
2. `Đã xử lý vi phạm / Giải trình nhầm lẫn`
3. `Hết thời hạn tạm khóa tài khoản`
4. `Theo yêu cầu từ người dùng / Phụ huynh`
5. `Khác (Tự nhập lý do)` *(Không bắt buộc)*

### 7.3. Modal Thông Báo Cho Người Dùng Bị Khóa (`AccountLockedModal.tsx`)
- Khi người dùng bị khóa cố gắng đăng nhập hoặc bị ngắt phiên:
  - Interceptor `lib/axios.ts` lưu `locked_account_info` (`lockReason`, `lockedAt`) vào `sessionStorage`.
  - Hiển thị Modal Cảnh báo sắc màu Destructive trên trang Đăng nhập.
  - Khối thông tin chi tiết:
    - **Lý do bị khóa:** Render chuỗi `lockReason` escaped.
    - **Thời gian khóa:** Format `lockedAt` theo chuẩn `HH:mm dd/MM/yyyy`.
  - Nút "Đã hiểu" đóng modal, dọn sạch phiên và ở lại trang Đăng nhập.

---

## 8. Verification Checklist (Kiểm thử Bắt buộc)

- [x] **Test Java Compilation:** `./gradlew compileJava` thành công.
- [x] **Test Frontend Type Check:** `npx tsc --noEmit` thành công 0 lỗi.
- [x] **Test Unit Tests:** `AdminUserServiceImplTest` vượt qua 100%.
- [ ] **Test Unlock Async Email:** Mở khóa tài khoản -> Kiểm tra gửi email thông báo mở khóa thành công.
- [ ] **Test Locked Account Reason Modal:** Tài khoản bị khóa đăng nhập -> Modal hiển thị đúng `lockReason` và `lockedAt`.
