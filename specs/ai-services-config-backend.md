# Specification: AI Services Configuration Backend (`MathClass-service`)

---

## 1. Feature
- **Feature Name:** AI Services Configuration & Dynamic Task Routing
- **Jira Ticket:** [MAT-253](https://phanvanluan611996.atlassian.net/browse/MAT-253)
- **Target Subsystem:** `MathClass-service` (Backend Microservice)
- **Target Users:** System Administrator (Quản trị viên hệ thống)

---

## 2. Business Goal
Cung cấp cho Quản trị viên công cụ quản lý tập trung các nhà cung cấp AI (Google Gemini, OpenAI, Anthropic...), mã hóa bảo mật tuyệt đối các API Key (AES-256-GCM), kiểm tra kết nối 2 bước linh hoạt, tự động cân bằng tải và chuyển đổi dự phòng (Auto Failover / Round-Robin), đồng thời cho phép phân công các phiên bản Model AI phù hợp nhất cho từng tác vụ chuyên biệt nhằm tối ưu hóa chi phí vận hành và tốc độ phản hồi của hệ thống.

---

## 3. Functional Requirements

* **FR-1 (Provider Management):** Hỗ trợ Admin xem danh sách, thêm mới, cập nhật và bật/tắt (toggle active) các AI Providers (Google Gemini, OpenAI, Anthropic...).
* **FR-2 (Multi API Keys Management):** Hỗ trợ 1 Provider sở hữu N API Keys. Mỗi Key có tên gợi nhớ (`keyName`), chỉ số ưu tiên (`priority`), trạng thái (`ACTIVE`, `EXHAUSTED_QUOTA`, `INVALID`) và được mã hóa lưu trữ bằng AES-256-GCM.
* **FR-3 (Two-Step Connection Verification):** 
  * *Bước 1 (List Models API):* Kiểm tra tính hợp lệ của API Key (phát hiện lỗi `401 Unauthorized`).
  * *Bước 2 (Lightweight Prompt API):* Gửi prompt 1-token để xác thực Quota/Credits (phát hiện lỗi `429 Too Many Requests`) và quyền truy cập Model (phát hiện lỗi `403/404`). Đo độ trễ phản hồi (ms).
* **FR-4 (Task Routing Assignment):** Cho phép Admin gán Provider, phiên bản Model (`model_name`), `temperature`, `max_tokens` cho từng loại tác vụ hệ thống (`CANVAS_LATEX`, `SUBMISSION_GRADING`, `QUESTION_GEN`, `HINT_RECOMMENDATION`, `ERROR_ANALYSIS`).
* **FR-5 (Dynamic Key Selection Strategy):**
  * *Priority Failover:* Sử dụng Key có `priority` cao nhất đang `ACTIVE`. Tự động đánh dấu `EXHAUSTED_QUOTA` và chuyển sang Key tiếp theo khi gặp lỗi 429/401.
  * *Round-Robin:* Xoay vòng luân phiên các Active Keys để chia đều lưu lượng requests.
* **FR-6 (Self-Healing & Manual Key Reset):** Key bị `EXHAUSTED_QUOTA` tự động mở lại sau **1 giờ** (TTL 1 hour). Đồng thời cho phép Admin bấm nút *"Reset Status"* thủ công trên Admin UI (áp dụng quy trình kiểm tra Test-First trước khi đổi trạng thái).
* **FR-7 (Per-Task Fallback Provider):** Cho phép cấu hình Provider dự phòng (`fallback_provider_id`) cho từng tác vụ. Nếu để `NULL` và Provider chính hết key, hệ thống ném lỗi `503 Service Unavailable` ngay lập tức.
* **FR-8 (API Key Masking):** Mọi API trả về Frontend chỉ hiển thị chuỗi Key dạng che mờ (8 ký tự đầu + `***` + 4 ký tự cuối, ví dụ: `AIzaSyD8***x9K4`).

---

## 4. Business Rules

* **BR-1 (Encryption Standard & Secret Management):** API Key bắt buộc phải mã hóa/giải mã thông qua `AesGcmEncryptionService` (hoặc `EncryptionService`) bằng thuật toán AES-256-GCM. Khóa gốc (Secret Master Key) được quản lý tập trung và nạp qua **Infisical Secret Management** (hoặc nạp qua biến môi trường `${AI_ENCRYPTION_MASTER_KEY}` khi chạy local offline) theo chuẩn 12-Factor App.
* **BR-2 (Zero Key Exposure):** API Key dạng Plaintext tuyệt đối không được ghi ra file Log, System Console, hoặc trả nguyên bản về REST API Response.
* **BR-3 (Auto Quota Transition):** Khi gọi AI trả về HTTP `429 Too Many Requests`, hệ thống phải cập nhật trạng thái Key thành `EXHAUSTED_QUOTA`.
* **BR-4 (Auto Invalid Transition):** Khi gọi AI trả về HTTP `401 Unauthorized`, hệ thống phải cập nhật trạng thái Key thành `INVALID`.
* **BR-5 (Test-First Key Reset):** Khi Admin bấm Reset một Key đang `EXHAUSTED_QUOTA`, hệ thống phải chạy quy trình Test Connection 2 bước. Chỉ khi Test thành công (200 OK) mới chuyển sang `ACTIVE`. Nếu vẫn lỗi 429, giữ nguyên `EXHAUSTED_QUOTA` và thông báo lỗi.
* **BR-6 (Task Fallback Rule):** Khi Provider chính của Task X hết key:
  * Nếu `fallback_provider_id != NULL`: Tự động chuyển sang sử dụng Provider dự phòng.
  * Nếu `fallback_provider_id == NULL`: Ném ngoại lệ `NoAvailableApiKeyException` (HTTP 503).
* **BR-7 (High Performance Caching):** Sử dụng Spring Cache với Caffeine In-Memory Provider cho `ai_task_configs` (TTL 10 phút). Mọi thao tác cập nhật cấu hình của Admin phải kích hoạt `@CacheEvict` để dọn dẹp cache tức thì.

---

## 5. Data Model

Tất cả các Entities trong module AI Configuration đều tuân thủ quy chuẩn dự án, kế thừa từ `com.codegym.mathclass.common.entity.BaseEntity` (kế thừa `id` kiểu `long`, `createdAt` và `updatedAt`).

### 5.1. Entity `AiProvider` (`ai_providers`)
- **Java Class:** `com.codegym.mathclass.aiconfig.entity.AiProvider extends BaseEntity`
```sql
CREATE TABLE ai_providers (
    id BIGSERIAL PRIMARY KEY,
    provider_code VARCHAR(50) NOT NULL UNIQUE,
    provider_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255),
    key_selection_strategy VARCHAR(30) DEFAULT 'PRIORITY_FAILOVER',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 5.2. Entity `AiApiKey` (`ai_api_keys`)
- **Java Class:** `com.codegym.mathclass.aiconfig.entity.AiApiKey extends BaseEntity`
```sql
CREATE TABLE ai_api_keys (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    key_name VARCHAR(100) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    priority INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 5.3. Entity `AiTaskConfig` (`ai_task_configs`)
- **Java Class:** `com.codegym.mathclass.aiconfig.entity.AiTaskConfig extends BaseEntity`
```sql
CREATE TABLE ai_task_configs (
    id BIGSERIAL PRIMARY KEY,
    task_code VARCHAR(50) NOT NULL UNIQUE,
    task_name VARCHAR(100) NOT NULL,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id),
    fallback_provider_id BIGINT REFERENCES ai_providers(id),
    model_name VARCHAR(100) NOT NULL,
    temperature DOUBLE PRECISION DEFAULT 0.7,
    max_tokens INTEGER DEFAULT 2048,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

## 6. API Contract

### 6.1. Fetch AI Configurations Overview
- **HTTP Method:** `GET`
- **Path:** `/api/admin/ai-configs`
- **Authorization:** `@PreAuthorize("hasRole('ADMIN')")`
- **Response `200 OK`:**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "providers": [
      {
        "id": 1,
        "providerCode": "GEMINI",
        "providerName": "Google Gemini API",
        "baseUrl": null,
        "keySelectionStrategy": "PRIORITY_FAILOVER",
        "isActive": true,
        "apiKeys": [
          {
            "id": 101,
            "keyName": "Gemini Key Main",
            "maskedApiKey": "AIzaSyD8***x9K4",
            "status": "ACTIVE",
            "priority": 1,
            "isActive": true,
            "lastUsedAt": "2026-07-30T09:15:00"
          }
        ]
      }
    ],
    "taskConfigs": [
      {
        "id": 1,
        "taskCode": "CANVAS_LATEX",
        "taskName": "Nhận diện Canvas & Công thức LaTeX",
        "providerId": 1,
        "fallbackProviderId": null,
        "modelName": "gemini-1.5-flash",
        "temperature": 0.7,
        "maxTokens": 2048,
        "isEnabled": true
      }
    ]
  }
}
```

### 6.2. Create / Update Provider & API Keys
- **HTTP Method:** `POST` | `PUT`
- **Path:** `/api/admin/ai-configs/providers` | `/api/admin/ai-configs/providers/{id}`
- **Authorization:** `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body:**
```json
{
  "providerCode": "GEMINI",
  "providerName": "Google Gemini API",
  "baseUrl": null,
  "keySelectionStrategy": "PRIORITY_FAILOVER",
  "isActive": true,
  "apiKeys": [
    {
      "keyName": "Gemini Key Main",
      "rawApiKey": "AIzaSyD8xK9mP2wQ1vR3tY5uI7oO4x9K4",
      "priority": 1,
      "isActive": true
    }
  ]
}
```

### 6.3. Test Connection (2-Step Verification)
- **HTTP Method:** `POST`
- **Path:** `/api/admin/ai-configs/test-connection`
- **Authorization:** `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body:**
```json
{
  "providerCode": "GEMINI",
  "rawApiKey": "AIzaSyD8xK9mP2wQ1vR3tY5uI7oO4x9K4",
  "baseUrl": null,
  "testModel": "gemini-1.5-flash"
}
```
- **Response `200 OK`:**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "success": true,
    "message": "Kết nối thành công!",
    "latencyMs": 142
  }
}
```

### 6.4. Manual Reset API Key Status
- **HTTP Method:** `POST`
- **Path:** `/api/admin/ai-configs/keys/{keyId}/reset`
- **Authorization:** `@PreAuthorize("hasRole('ADMIN')")`

---

## 7. Validation Specifications

```java
public class TaskConfigRequestDTO {
    @NotNull(message = "Provider ID không được để trống")
    private Long providerId;

    private Long fallbackProviderId;

    @NotBlank(message = "Tên Model không được để trống")
    private String modelName;

    @NotNull(message = "Temperature không được để trống")
    @DecimalMin(value = "0.0", message = "Temperature tối thiểu là 0.0")
    @DecimalMax(value = "2.0", message = "Temperature tối đa là 2.0")
    private Double temperature;

    @NotNull(message = "Max tokens không được để trống")
    @Min(value = 1, message = "Max tokens tối thiểu phải là 1")
    @Max(value = 32768, message = "Max tokens tối đa là 32768")
    private Integer maxTokens;

    private Boolean isEnabled;
}

public class ApiKeyRequestDTO {
    @NotBlank(message = "Tên API Key không được để trống")
    @Size(min = 3, max = 100, message = "Tên API Key phải từ 3 đến 100 ký tự")
    private String keyName;

    @NotBlank(message = "API Key không được để trống")
    private String rawApiKey;

    @Min(value = 1, message = "Priority tối thiểu là 1")
    private Integer priority;
}
```

---

## 8. Implementation Constraints

* **Programming Language & Framework:** Java 21 LTS, Spring Boot 4.x, Spring Data JPA.
* **Database:** PostgreSQL (dùng `gen_random_uuid()` cho UUID Primary Keys).
* **Caching:** Spring Cache với Caffeine In-Memory Cache.
* **Architecture Pattern:** Package by Feature (`com.codegym.mathclass.aiconfig`), Constructor Injection thông qua `@RequiredArgsConstructor`.
* **Security Constraints:** Không lưu băm 1 chiều, mã hóa 2 chiều AES-256-GCM với IV 96-bit ngẫu nhiên cho từng bản ghi.

---

## 9. Acceptance Criteria

### Scenario 1: Admin configures new Provider & API Keys successfully
* **Given** Admin có quyền `ROLE_ADMIN` truy cập trang Admin Console.
* **When** Admin gửi request tạo mới Provider `GEMINI` với API Key `AIzaSyD8xK9mP2wQ1vR3tY5uI7oO4x9K4`.
* **Then** API Key được mã hóa AES-256-GCM trước khi lưu xuống CSDL, và Response DTO trả về hiển thị chuỗi che mờ `AIzaSyD8***x9K4`.

### Scenario 2: Test Connection handles 429 Quota Exhausted accurately
* **Given** Admin bấm "Test Connection" với một API Key đã hết hạn ngạch.
* **When** Bước 1 (List Models) thành công nhưng Bước 2 (Lightweight Prompt) trả về HTTP `429`.
* **Then** Hệ thống trả về `success = false` với thông báo: *"API Key đúng nhưng Tài khoản đã HẾT QUOTA / Credits"*.

### Scenario 3: Auto-Failover switches to secondary key on 429 Error
* **Given** Task `CANVAS_LATEX` dùng Provider Gemini có Key 1 (`priority = 1`) và Key 2 (`priority = 2`).
* **When** Request gọi AI bị lỗi HTTP `429` từ Key 1.
* **Then** Key 1 chuyển sang `EXHAUSTED_QUOTA`, request tự động retry thành công với Key 2 mà người dùng không nhận ra sự cố.

### Scenario 4: Task Fallback Provider triggers when main Provider is empty
* **Given** Task `SUBMISSION_GRADING` có Provider chính là Gemini (toàn bộ key hết) và Provider dự phòng `fallback_provider_id` là OpenAI.
* **When** Người dùng thực hiện chấm bài.
* **Then** Hệ thống tự động chuyển sang sử dụng OpenAI để xử lý request chấm bài.

---

## 10. Task Checklist (Mapped to Jira Sub-tasks)

- [ ] **[MAT-271] Data Model & Migration:** Tạo Entities `AiProvider`, `AiApiKey`, `AiTaskConfig` & script Migration CSDL PostgreSQL.
- [ ] **[MAT-272] AES-256-GCM Crypto Service:** Hiện thực `AesGcmEncryptionService`, `EnvVarMasterKeyProvider` & `ApiKeyCryptoConverter`.
- [ ] **[MAT-273] Dynamic Key Selection Service:** Hiện thực `AiKeySelectionService` hỗ trợ `PRIORITY_FAILOVER` & `ROUND_ROBIN`.
- [ ] **[MAT-274] Two-Step Connection Test Service:** Hiện thực `AiConnectionTestService` kiểm tra List Models & Lightweight Prompt.
- [ ] **[MAT-275] Caffeine Spring Cache:** Cấu hình Cache Caffeine cho `ai_task_configs` & cài đặt `@CacheEvict`.
- [ ] **[MAT-276] REST API Admin Controller:** Xây dựng `AdminAiConfigController`, phân quyền `@PreAuthorize("hasRole('ADMIN')")` & Masking DTO.
- [ ] **[MAT-277] Unit & Integration Tests:** Viết test suites cho Security, Key Selection, Test Connection & Controller APIs.
