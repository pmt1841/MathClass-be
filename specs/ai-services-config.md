# Specification: AI Services Configuration (`MathClass-service`)

# 1. Giới thiệu

## 1.1. Mục tiêu

Cung cấp cho Administrator một giao diện quản lý tập trung để cấu hình toàn bộ các nhà cung cấp dịch vụ AI (Provider) mà hệ thống sử dụng, bao gồm:

- Quản lý Provider (thêm, sửa, xóa, kích hoạt/vô hiệu hóa).
- Quản lý API Key cho từng Provider (thêm, xóa, kích hoạt/vô hiệu hóa, kiểm tra trạng thái).
- Thử kết nối tới Provider với một API Key và Model cụ thể.
- Cấu hình Model và tham số cho từng tác vụ (Task) trong hệ thống.

## 1.2. Phạm vi

Hệ thống đảm nhiệm việc lưu trữ, mã hóa, và cung cấp các cấu hình AI cho các module khác. Module này chỉ dành cho Administrator; người dùng thông thường không có quyền truy cập.

## 1.3. Giả định

- Hệ thống đã có cơ chế xác thực (Authentication) và phân quyền (Authorization) riêng, chỉ cho phép Admin truy cập các API được mô tả.
- Số lượng Provider và Key không quá lớn (dưới 1000), do đó có thể sử dụng cache toàn bộ cấu hình.
- Các Provider đều hỗ trợ REST API với chuẩn chung (như OpenAI, Gemini, Claude, ...).
- Hệ thống sẽ xử lý lỗi từ Provider và thực hiện retry/fallback theo chiến lược đã định nghĩa.

---

# 2. Yêu cầu chức năng

## 2.1. Quản lý Provider

Administrator có thể thực hiện các thao tác sau:

| Chức năng | Mô tả |
| :--- | :--- |
| **Danh sách Provider** | Xem tất cả Provider với thông tin: ID, Code, Name, Base URL, Strategy, Status (Active/Inactive), Created At, Updated At. |
| **Tạo Provider** | Tạo mới với các trường bắt buộc: Code (duy nhất, viết hoa), Name, Base URL (HTTPS), Strategy (PRIORITY hoặc ROUND_ROBIN). |
| **Sửa Provider** | Cập nhật Name, Base URL, Strategy, Status. Không được thay đổi Code. |
| **Vô hiệu hóa** | Chuyển Status thành INACTIVE. Provider bị vô hiệu hóa sẽ không được sử dụng cho bất kỳ Task nào; các Task đang dùng Provider này sẽ báo lỗi khi gọi. |
| **Kích hoạt** | Chuyển Status thành ACTIVE. |
| **Xóa Provider** | Chỉ xóa được khi không có TaskConfig nào tham chiếu đến Provider đó (kể cả Task đang bị vô hiệu hóa). |

## 2.2. Quản lý API Key

Mỗi Provider có thể có nhiều API Key.

| Chức năng | Mô tả |
| :--- | :--- |
| **Thêm Key** | Cung cấp Name (tùy chọn), API Key (dạng plaintext), Priority (số nguyên, mặc định 0). Hệ thống mã hóa key trước khi lưu. |
| **Vô hiệu hóa Key** | Chuyển status thành INACTIVE – key không được sử dụng. |
| **Kích hoạt Key** | Chuyển status thành ACTIVE. |
| **Xóa Key** | Xóa vĩnh viễn khỏi DB. |
| **Kiểm tra trạng thái** | Gửi yêu cầu xác thực đến Provider để xác minh key còn hiệu lực, có quota, v.v. (xem mục 2.4). |
| **Danh sách Key** | Xem tất cả Key của một Provider với thông tin: ID, Name, Priority, Status, Last Used (thời gian sử dụng gần nhất), nhưng **không** bao gồm encryptedKey. |

## 2.3. Cấu hình Task

Hệ thống có các tác vụ AI cố định (ví dụ: Question Generation, Assignment Grading, Content Summarization, Canvas LaTeX, ...). Administrator cấu hình cho từng Task:

- Provider được sử dụng.
- Model (tên model của Provider, ví dụ `gemini-2.5-flash`, `gpt-4o`).
- Temperature (giá trị từ 0.0 đến 2.0).
- Max Tokens (số nguyên > 0).
- Trạng thái Enabled – cho phép Task sử dụng cấu hình này hay không (mặc định true).

Hệ thống chỉ cho phép mỗi Task có duy nhất một cấu hình (không hỗ trợ nhiều cấu hình cho cùng Task).

## 2.4. Kiểm tra kết nối (Test Connection)

Administrator có thể kiểm tra tính hợp lệ của một cặp (Provider, API Key, Model, Base URL) bằng cách gửi yêu cầu thử nghiệm đến Provider.

**Đầu vào:**

- `provider` (code) – xác định Provider.
- `apiKey` (plaintext) – key cần kiểm tra.
- `model` – tên model.
- `baseUrl` – (tùy chọn) nếu không cung cấp, dùng Base URL mặc định của Provider; nếu có, dùng để test.

**Quy trình:**

- Hệ thống gửi một yêu cầu (ví dụ: liệt kê model hoặc kiểm tra quota) tới Provider.
- Ghi nhận kết quả:
  - **Thành công:** Key hợp lệ, model tồn tại, có quyền truy cập và còn quota.
  - **Thất bại:** Trả về mã lỗi và thông báo chi tiết (ví dụ: `401` – Invalid Key, `404` – Model not found, `429` – Quota exceeded, `500` – Provider error).
- Đo thời gian phản hồi (latency) và trả về.

## 2.5. Chiến lược chọn API Key (Key Selection Strategy)

Khi module AI cần gọi Provider, nó sẽ sử dụng Strategy được định nghĩa cho Provider đó.

- **PRIORITY:** Luôn chọn Key có `priority` cao nhất (số lớn nhất) và đang ACTIVE. Nếu key đó thất bại (401, 429, 500), hệ thống sẽ chuyển sang key có priority thấp hơn tiếp theo.
- **ROUND_ROBIN:** Luân phiên giữa các Key ACTIVE. Con trỏ được duy trì trên toàn hệ thống và được cập nhật nguyên tử (atomic). Khi một key thất bại, nó sẽ bị bỏ qua và chuyển sang key tiếp theo.

**Xử lý lỗi khi gọi AI:**

- `401 Unauthorized`: Đánh dấu key là `INACTIVE` và gửi cảnh báo tới Admin (qua log hoặc email). Chuyển sang key khác (nếu có).
- `429 Too Many Requests / Quota exceeded`: Chuyển sang key khác ngay lập tức. Key hiện tại vẫn giữ ACTIVE nhưng sẽ bị tạm thời bỏ qua trong vòng 5 phút (cooldown) để tránh lặp lỗi.
- `5xx Server Error`: Retry tối đa 3 lần với cùng key (backoff 1s, 2s, 4s). Nếu vẫn thất bại, chuyển sang key khác.
- `Timeout`: Nếu quá thời gian chờ cấu hình (mặc định 30s), coi như thất bại và xử lý như 5xx.
- Nếu tất cả các key đều thất bại, hệ thống trả về lỗi cho caller.

---

# 3. Yêu cầu phi chức năng

| Yêu cầu | Mô tả |
| :--- | :--- |
| **Bảo mật** | - API Key được mã hóa bằng AES-256-GCM trước khi lưu vào DB. Khóa mã hóa được lưu trong biến môi trường (hoặc sử dụng KMS).<br>- Không bao giờ trả về plaintext key qua API.<br>- Không log plaintext key. |
| **Hiệu năng** | - Response time của các API quản lý (không bao gồm gọi Provider) < 300ms.<br>- Sử dụng cache để lưu cấu hình Provider và Key (TTL 5 phút hoặc invalidate khi có thay đổi). |
| **Độ tin cậy** | - Hỗ trợ threadsafe khi chọn key (cho ROUND_ROBIN).<br>- Có cơ chế retry và fallback khi gọi AI. |
| **Audit Log** | Ghi lại tất cả thao tác quản lý (tạo, sửa, xóa, test) với thông tin: người thực hiện, thời gian, hành động, dữ liệu cũ/mới (nếu có). |
| **Khả năng mở rộng** | Dễ dàng thêm Provider mới, Strategy mới mà không ảnh hưởng module khác. |
| **Tính sẵn sàng** | Hệ thống phải hoạt động ngay cả khi một vài Provider không khả dụng (chuyển sang Provider dự phòng nếu có cấu hình). |

---

# 4. Mô hình dữ liệu

*Ghi chú:* Tất cả các Entity JPA trong dự án kế thừa từ `com.codegym.mathclass.common.entity.BaseEntity` (chứa `id` kiểu `long` / `BIGSERIAL`, `createdAt`, `updatedAt`).

## 4.1. Bảng `provider`

| Cột | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGSERIAL / UUID | PK | Khóa chính (kế thừa `BaseEntity`) |
| `code` | VARCHAR(50) | NOT NULL, UNIQUE | Mã định danh duy nhất, chỉ chứa chữ hoa, số và dấu gạch dưới |
| `name` | VARCHAR(100) | NOT NULL | Tên hiển thị |
| `base_url` | VARCHAR(255) | NOT NULL | URL gốc của Provider, phải là HTTPS |
| `strategy` | VARCHAR(20) | NOT NULL, DEFAULT 'PRIORITY' | 'PRIORITY' hoặc 'ROUND_ROBIN' |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | 'ACTIVE' hoặc 'INACTIVE' |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm tạo (kế thừa `BaseEntity`) |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm cập nhật lần cuối (kế thừa `BaseEntity`) |

## 4.2. Bảng `api_key`

| Cột | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGSERIAL / UUID | PK | Khóa chính (kế thừa `BaseEntity`) |
| `provider_id` | BIGINT / UUID | FK (provider.id), NOT NULL | Provider chứa key này |
| `name` | VARCHAR(100) | NULL | Tên gợi nhớ (tùy chọn) |
| `encrypted_key` | TEXT | NOT NULL | API Key đã mã hóa (AES-256-GCM) |
| `priority` | INT | NOT NULL, DEFAULT 0 | Độ ưu tiên (cao hơn = ưu tiên hơn) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | 'ACTIVE', 'INACTIVE' |
| `last_used` | TIMESTAMP | NULL | Thời điểm key được sử dụng lần cuối |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm tạo (kế thừa `BaseEntity`) |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm cập nhật (kế thừa `BaseEntity`) |

## 4.3. Bảng `task_config`

| Cột | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `task` | VARCHAR(50) | PK | Mã tác vụ (ví dụ: 'QUESTION_GEN') |
| `provider_id` | BIGINT / UUID | FK (provider.id), NOT NULL | Provider được gán |
| `model` | VARCHAR(100) | NOT NULL | Tên model (ví dụ: 'gemini-2.5-flash') |
| `temperature` | DECIMAL(3,2) | NOT NULL, CHECK(0 <= temperature AND temperature <= 2) | Độ ngẫu nhiên phản hồi |
| `max_token` | INT | NOT NULL, CHECK(max_token > 0) | Giới hạn số token đầu ra |
| `enabled` | BOOLEAN | NOT NULL, DEFAULT TRUE | Cho phép sử dụng cấu hình này |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm cập nhật lần cuối (kế thừa `BaseEntity`) |

---

# 5. API Specifications

Tất cả các endpoint yêu cầu xác thực và phân quyền Admin.

## 5.1. Quản lý Provider

### 5.1.1. Lấy danh sách Provider

- **Endpoint:** `GET /api/v1/providers`
- **Response (200 OK):**

```json
{
  "data": [
    {
      "id": "uuid",
      "code": "GEMINI",
      "name": "Google Gemini",
      "baseUrl": "https://api.gemini.com/v1",
      "strategy": "PRIORITY",
      "status": "ACTIVE",
      "createdAt": "2026-07-30T10:00:00Z",
      "updatedAt": "2026-07-30T10:00:00Z"
    }
  ]
}
```

### 5.1.2. Tạo Provider

- **Endpoint:** `POST /api/v1/providers`
- **Request Body:**

```json
{
  "code": "GEMINI",
  "name": "Google Gemini",
  "baseUrl": "https://api.gemini.com/v1",
  "strategy": "PRIORITY"
}
```

- **Response (201 Created):** Dữ liệu Provider như trên.
- **Lỗi:**
  - `400` – Validation lỗi (thiếu trường, code trùng, URL không hợp lệ, ...).
  - `409` – Code đã tồn tại.

### 5.1.3. Cập nhật Provider

- **Endpoint:** `PUT /api/v1/providers/{id}`
- **Request Body:** Tương tự POST nhưng `code` không được gửi (không thay đổi được).
- **Response (200 OK):** Dữ liệu Provider đã cập nhật.
- **Lỗi:** `404` – Provider không tồn tại, `400` – Validation.

### 5.1.4. Xóa Provider

- **Endpoint:** `DELETE /api/v1/providers/{id}`
- **Response:** `204 No Content` nếu thành công.
- **Lỗi:**
  - `400` – Provider đang được Task sử dụng (trả về danh sách Task đang dùng).
  - `404` – Không tìm thấy.

## 5.2. Quản lý API Key

### 5.2.1. Lấy danh sách Key của Provider

- **Endpoint:** `GET /api/v1/providers/{providerId}/keys`
- **Response (200 OK):**

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Production Key",
      "priority": 10,
      "status": "ACTIVE",
      "lastUsed": "2026-07-30T09:00:00Z",
      "createdAt": "2026-07-30T08:00:00Z",
      "updatedAt": "2026-07-30T08:00:00Z"
    }
  ]
}
```

*Lưu ý: Không trả về `encryptedKey` hay bất kỳ dạng key nào.*

### 5.2.2. Thêm Key

- **Endpoint:** `POST /api/v1/providers/{providerId}/keys`
- **Request Body:**

```json
{
  "name": "Production Key",
  "apiKey": "AIzaSy...",
  "priority": 10
}
```

- **Response (201 Created):** Dữ liệu Key như trên (không có `encryptedKey`).
- **Lỗi:** `400` – Validation, `404` – Provider không tồn tại.

### 5.2.3. Xóa Key

- **Endpoint:** `DELETE /api/v1/keys/{keyId}`
- **Response:** `204 No Content`.

### 5.2.4. Cập nhật trạng thái Key

- **Endpoint:** `PATCH /api/v1/keys/{keyId}`
- **Request Body:**

```json
{
  "status": "INACTIVE"
}
```

- **Response (200 OK):** Dữ liệu Key đã cập nhật.
- **Lỗi:** `404`, `400`.

### 5.2.5. Kiểm tra trạng thái Key

- **Endpoint:** `POST /api/v1/keys/{keyId}/verify`
- **Hành động:** Hệ thống lấy key đã lưu (giải mã) và gửi yêu cầu test tới Provider tương ứng (tương tự mục 2.4).
- **Response (200 OK):**

```json
{
  "valid": true,
  "latencyMs": 120,
  "message": "Key is valid and has quota"
}
```

## 5.3. Test Connection (nhập tạm)

- **Endpoint:** `POST /api/v1/providers/test`
- **Request Body:**

```json
{
  "providerCode": "GEMINI",
  "apiKey": "AIzaSy...",
  "model": "gemini-2.5-flash",
  "baseUrl": "https://api.gemini.com/v1"
}
```

- **Response (200 OK) nếu thành công:**

```json
{
  "success": true,
  "latencyMs": 95,
  "message": "Connection successful"
}
```

- **Response (4xx/5xx) nếu thất bại:**

```json
{
  "success": false,
  "errorCode": "AUTH_FAILED",
  "message": "Invalid API Key",
  "latencyMs": 45
}
```

*Có thể trả về mã lỗi như `INVALID_KEY`, `MODEL_NOT_FOUND`, `QUOTA_EXHAUSTED`, `PROVIDER_ERROR`, `TIMEOUT`.*

## 5.4. Cấu hình Task

### 5.4.1. Lấy cấu hình Task

- **Endpoint:** `GET /api/v1/tasks/{task}`
- **Response (200 OK):**

```json
{
  "task": "QUESTION_GEN",
  "providerId": "uuid",
  "model": "gemini-2.5-flash",
  "temperature": 0.7,
  "maxToken": 1024,
  "enabled": true,
  "updatedAt": "2026-07-30T10:00:00Z"
}
```

- **Lỗi:** `404` – Task chưa có cấu hình (có thể trả về mặc định nếu có).

### 5.4.2. Cập nhật cấu hình Task

- **Endpoint:** `PUT /api/v1/tasks/{task}`
- **Request Body:**

```json
{
  "providerId": "uuid",
  "model": "gemini-2.5-flash",
  "temperature": 0.7,
  "maxToken": 1024,
  "enabled": true
}
```

- **Response (200 OK):** Dữ liệu cấu hình đã cập nhật.
- **Lỗi:** `400` – Validation, `404` – Provider không tồn tại, `409` – Nếu Provider bị INACTIVE.

## 5.5. Mã lỗi chung

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `INVALID_REQUEST` | 400 | Dữ liệu đầu vào không hợp lệ |
| `UNAUTHORIZED` | 401 | Chưa xác thực |
| `FORBIDDEN` | 403 | Không có quyền truy cập |
| `NOT_FOUND` | 404 | Tài nguyên không tồn tại |
| `CONFLICT` | 409 | Xung đột (ví dụ code trùng) |
| `INTERNAL_ERROR` | 500 | Lỗi hệ thống |

---

# 6. Chiến lược caching

- Cache cấu hình Provider và danh sách Key active theo từng Provider.
- Cache được lưu trong bộ nhớ (Caffeine) với TTL = 5 phút.
- Khi có thay đổi (tạo/sửa/xóa Provider, Key, Task), tự động invalidate cache tương ứng.
- Khi gọi AI, module AI sẽ lấy cấu hình từ cache (nếu có), nếu không thì truy vấn DB và cập nhật cache.

---

# 7. Yêu cầu về logging và giám sát

- **Audit Log:** Ghi log các hành động quản lý. Mỗi log bao gồm: `username`, `timestamp`, `action` (`CREATE_PROVIDER`, `UPDATE_KEY`, ...), `target` (ID của đối tượng), `oldValue`, `newValue` (nếu có), `ip` (nếu có).
- **Application Log:** Ghi các lỗi khi gọi AI, retry, fallback, key bị vô hiệu hóa tự động. Không log plaintext key.
- **Metrics:** Thống kê số lần gọi AI thành công/thất bại theo Provider, Key, Task; latency trung bình; số lần retry; số lần key bị vô hiệu.

---

# 8. Hướng dẫn triển khai

## 8.1. Công nghệ đề xuất

- **Backend:** Java 21 với Spring Boot 4.x.
- **Database:** PostgreSQL (hỗ trợ JSONB nếu cần mở rộng).
- **Cache:** Caffeine (in-memory).
- **Mã hóa:** Spring Security Crypto hoặc JCA với AES-GCM.
- **Logging:** SLF4J + Logback, tích hợp với ELK nếu có.

## 8.2. Các lớp cần xây dựng

- **Entity:** `Provider`, `ApiKey`, `TaskConfig` (JPA, kế thừa `BaseEntity`).
- **Repository:** JPA Repository với các phương thức tìm kiếm.
- **DTO:** Request/Response classes.
- **Mapper:** Map giữa Entity và DTO (dùng MapStruct).
- **Validator:** Custom validation cho code, URL, v.v.
- **Service:** `ProviderService`, `ApiKeyService`, `TaskConfigService`, `ConnectionTestService`, `KeySelectionService` (xử lý logic chọn key và fallback).
- **EncryptionService:** Mã hóa/giải mã API Key.
- **CacheService:** Quản lý cache.
- **RestController:** Các endpoint REST.
- **ExceptionHandler:** Xử lý ngoại lệ toàn cục (`ControllerAdvice`).
- **Unit Tests:** JUnit + Mockito cho từng service.
- **Integration Tests:** TestRepository, Test API với Testcontainers.

## 8.3. Xử lý threadsafe cho Round Robin

- Sử dụng `AtomicInteger` cho con trỏ, lưu trong cache hoặc DB (nếu cần persistent). Khi chọn key, tăng con trỏ lên và lấy key theo vị trí (`mod` số key active).
- Cần đồng bộ khi có thay đổi danh sách key active (thêm/xóa) để tránh lỗi index.

## 8.4. Khởi tạo dữ liệu mẫu

Khi ứng dụng chạy lần đầu, có thể tạo sẵn một số Provider mặc định (OpenAI, Gemini) nhưng không có key – admin phải tự thêm.

---

# 9. Kịch bản kiểm tra (Acceptance Criteria)

1. Admin có thể tạo Provider mới với đầy đủ thông tin hợp lệ.
2. Không thể tạo Provider với code đã tồn tại.
3. Admin có thể thêm API Key cho Provider; key được mã hóa trong DB.
4. Admin có thể test kết nối với key mới nhập và nhận kết quả thành công/thất bại.
5. Admin có thể cấu hình Task với Provider và Model đã tồn tại.
6. Khi Provider bị vô hiệu hóa, Task sử dụng Provider đó sẽ không gọi được AI (trả về lỗi).
7. Khi một Key gặp lỗi 401, nó tự động bị vô hiệu hóa và hệ thống chuyển sang key khác (nếu có).
8. Với `Strategy = PRIORITY`, key có priority cao nhất luôn được chọn trước; khi key đó thất bại, chọn key thấp hơn.
9. Với `Strategy = ROUND_ROBIN`, các key được chọn luân phiên và threadsafe.
10. API không bao giờ trả về plaintext key.

---

# 10. Phụ lục – Biểu đồ tuần tự (tham khảo)

Khi gọi AI (module khác gọi đến Service AI):

1. Module AI gửi yêu cầu với `task` và `input`.
2. Service AI lấy cấu hình Task từ cache/DB.
3. Lấy Provider và danh sách Key active.
4. Áp dụng Strategy để chọn Key.
5. Gọi API Provider với Key và Model.
6. Nếu thành công, trả kết quả; nếu thất bại, thực hiện retry/fallback.
7. Cập nhật `last_used` cho Key được chọn (asynchronously).
8. Log kết quả, metrics.
