# [MAT-404] Đặc Tả Thiết Kế Kỹ Thuật Backend: Cấu Hình Đa Ngôn Ngữ (i18n) & API Lưu Ngôn Ngữ Người Dùng

Tài liệu đặc tả kỹ thuật chi tiết dành riêng cho dịch vụ Backend (`MathClass-service`).

---

## 1. Kiến Trúc & Source of Truth (Backend Scope)

### Phân định vai trò lưu trữ
- **Cơ sở dữ liệu (`users.language`):** Cơ sở dữ liệu Backend là **Source of Truth duy nhất** lưu trữ ngôn ngữ ưa thích vĩnh viễn của từng tài khoản người dùng (`User`).
- **Phản hồi AuthResponse:** Sau khi người dùng xác thực thành công (Login / Register / Refresh Token), Backend trả về thông tin `language` lưu trong DB thuộc về `AuthResponse` DTO để Frontend ghi nhận vào Cookie session.

### Quy tắc đồng bộ & chống ghi đè ngược (Conflict Prevention Rule)
- Backend **chỉ** cập nhật trường `language` của người dùng khi có yêu cầu hợp lệ được gửi trực tiếp đến endpoint `PATCH /api/v1/users/me/language`.
- Backend không tự động suy đoán hoặc ghi đè `users.language` thông qua header `Accept-Language` của các API thông thường.

---

## 2. Header Accept-Language & Thuật Toán Thương Lượng Locale (Locale Negotiation)

### Thuật toán phân tích & Khớp Locale
1. Trích xuất header `Accept-Language` từ HTTP Request.
2. Sắp xếp danh sách các ngôn ngữ theo thứ tự ưu tiên trọng số `q-factor` giảm dần (ví dụ `Accept-Language: fr;q=0.9, en;q=0.8` → ưu tiên `fr` trước, `en` sau).
3. Lặp qua danh sách đã sắp xếp, trích xuất mã ngôn ngữ ISO-639-1 2 ký tự (ví dụ `en-US` → `en`, `vi-VN` → `vi`).
4. Đối chiếu mã ngôn ngữ với danh sách `app.i18n.supported-locales` (khai báo tại `application.yml`). Chọn locale hợp lệ đầu tiên khớp danh sách.
5. **Trường hợp ký tự đại diện `*`:** Trả về `default-locale` (mặc định: `vi`).
6. **Trường hợp Header lỗi (Malformed / Invalid Syntax):** Tuyệt đối **không** trả về lỗi HTTP 400 Bad Request. Hệ thống tự động fallback an toàn về `default-locale` (`vi`). Quá trình thương lượng ngôn ngữ không bao giờ được làm sập request chỉ vì header bất thường.

### Startup Fail-Fast Gate
Kiểm tra tính hợp lệ của cấu hình hệ thống ngay khi khởi động: `default-locale` bắt buộc phải thuộc danh sách `supported-locales`.

---

## 3. API Specs: `PATCH /api/v1/users/me/language`

### Định tuyến & Bảo mật
- **Endpoint:** `PATCH /api/v1/users/me/language`
- **Yêu cầu Authentication:** Yêu cầu JWT Access Token hợp lệ trong header `Authorization: Bearer <token>`.
- **User Resolution:** Lấy thông tin người dùng hiện tại trực tiếp từ `SecurityContextHolder`. Tuyệt đối không nhận `userId` từ Query Parameter hay Request Body.

### Request Body (`UpdateUserLanguageRequest`)
```json
{
  "language": "en"
}
```

### Mã Phản Hồi HTTP (HTTP Status Codes)
- **`200 OK`:** Cập nhật thành công. Trả về `UserResponse` chứa thông tin `language` mới cập nhật.
- **`400 Bad Request`:** Mã ngôn ngữ không hợp lệ hoặc dữ liệu request vi phạm validation.
- **`401 Unauthorized`:** Token không hợp lệ hoặc hết hạn.

### Cấu Trúc Phản Hồi Lỗi Chuẩn (Machine-Readable Format)
```json
{
  "success": false,
  "code": "USER_LANGUAGE_INVALID",
  "message": "Ngôn ngữ được chọn không được hỗ trợ",
  "timestamp": "2026-09-24T18:15:00Z"
}
```

---

## 4. Validation & Configuration (OCP & Fail-Fast)

### Custom Annotation `@SupportedLocale`
- Định nghĩa annotation `@SupportedLocale` đính kèm validator `SupportedLocaleValidator`.
- Validator truy xuất danh sách `app.i18n.supported-locales` được nạp từ `application.yml`. **Tuyệt đối không** hardcode regex `vi|en` trong source code Java.

### Configuration Properties (`application.yml`)
```yaml
app:
  i18n:
    default-locale: vi
    supported-locales:
      - vi
      - en
```

### Startup Fail-Fast Check
Sử dụng `@PostConstruct` hoặc `ApplicationRunner` để validate cấu hình:
```java
if (!supportedLocales.contains(defaultLocale)) {
    throw new IllegalStateException("Default locale [" + defaultLocale + "] must be included in supported locales " + supportedLocales);
}
```

---

## 5. Database Migration Strategy (Flyway)

### Script Migration (`V14__add_language_to_users_table.sql`)
```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS language VARCHAR(10);
ALTER TABLE users ALTER COLUMN language SET DEFAULT 'vi';
UPDATE users SET language = 'vi' WHERE language IS NULL;
ALTER TABLE users ALTER COLUMN language SET NOT NULL;
```

### Chiến Lược Đảm Bảo Zero/Minimal Downtime
Đối với bảng `users` chứa dữ liệu lớn trên môi trường Production:
- Việc backfill dữ liệu `UPDATE` sẽ được chia thành các lô nhỏ (ví dụ 5,000 bản ghi/lô) chạy trong các transaction ngắn độc lập.
- Mục đích: **Hạn chế thời gian giữ lock và giảm nguy cơ lock contention/deadlock** gây gián đoạn các giao dịch đọc/ghi khác trên hệ thống.

---

## 6. Message Bundles (Single Source of Truth)

### Cấu trúc Resource Bundle
Chỉ duy trì 2 tệp thông báo chính trong `src/main/resources/i18n/`:
- `messages_vi.properties`: Bộ từ điển thông báo chuẩn Tiếng Việt.
- `messages_en.properties`: Bộ từ điển thông báo chuẩn Tiếng Anh.

> ⚠️ **Triệt tiêu hai nguồn dữ liệu:** Không tạo tệp `messages.properties` song song chứa chuỗi Tiếng Việt để tránh tình trạng bất đồng bộ nội dung.

### Cấu hình `MessageSource` (Spring Bean)
- Cấu hình `ResourceBundleMessageSource` đặt `defaultLocale = Locale.of("vi")`.
- Cấu hình `basename = i18n/messages`.

---

## 7. Header `Vary: Accept-Language` Cho Localized Response

### Quy tắc đính kèm Header `Vary`
- **Tự động bổ sung `Vary: Accept-Language`:** Áp dụng cho các HTTP Response chứa nội dung thực sự được localized theo header request (ví dụ: Thông báo lỗi validation, error code descriptions, localized messages).
- **Không đính kèm `Vary`:** Đối với các API chỉ trả về dữ liệu thô (raw JSON data, file binary, metrics, image IDs) không phụ thuộc vào ngôn ngữ request, tránh gây ra hiện tượng phân mảnh cache (cache fragmentation) trên CDN / Reverse Proxy.

---

## 8. Kịch Bản Kiểm Thử Backend (Backend Test Suite Matrix)

### 8.1 Accept-Language Parsing & Fallback Tests
- Test parse header `Accept-Language` với trọng số `q-factor` phức tạp (ví dụ `fr;q=0.9, en;q=0.8`).
- Test header chứa wildcard `*` $\rightarrow$ Trả về `default-locale` (`vi`).
- Test header bị lỗi cú pháp / malformed $\rightarrow$ Fallback về `vi`, đảm bảo không bao giờ trả lỗi 400.

### 8.2 Startup & Validation Tests
- Test Startup Fail-Fast: Cố tình cấu hình `default-locale` không nằm trong `supported-locales` $\rightarrow$ Ứng dụng ném `IllegalStateException` dừng startup.
- Test `@SupportedLocale` Validator: Gửi `language: "fr"` (chưa hỗ trợ) $\rightarrow$ Validator trả lỗi validation kèm error code `USER_LANGUAGE_INVALID`.

### 8.3 Endpoint `PATCH /api/v1/users/me/language` Integration Tests
- Test 200 OK: Gửi `language: "en"` kèm token hợp lệ $\rightarrow$ DB cập nhật `language = 'en'`, API trả về `UserResponse`.
- Test 400 Bad Request: Gửi body rỗng hoặc giá trị invalid $\rightarrow$ Trả lỗi 400 Bad Request kèm JSON cấu trúc chuẩn.
- Test 401 Unauthorized: Không gửi JWT token $\rightarrow$ Trả 401 Unauthorized.

### 8.4 Header Vary Verification Tests
- Kiểm tra HTTP Response Headers của API localized error response chứa header `Vary: Accept-Language`.
