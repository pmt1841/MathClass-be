# Specification: System Prompt Management (`MathClass-service`)

# 1. Giới thiệu

## 1.1. Mục tiêu

Cung cấp cho Administrator một giao diện quản lý tập trung để quản lý toàn bộ các câu lệnh mẫu (System Prompts) điều khiển AI trong hệ thống `MathClass`, bao gồm:

- Quản lý các mẫu System Prompt cho từng tác vụ AI (thêm, sửa, xóa, xem chi tiết, kích hoạt/vô hiệu hóa).
- Hỗ trợ biến môi trường (Variables) động trong prompt (ví dụ: `{{grade_level}}`, `{{subject}}`, `{{student_answer}}`) kèm cơ chế kiểm tra tính hợp lệ nghiêm ngặt (Strict Validation).
- Khôi phục câu lệnh mặc định gốc (Reset to Default) khi prompt tùy chỉnh gặp sự cố.
- Quản lý lịch sử thay đổi phiên bản (Prompt Versioning History) và hỗ trợ khôi phục về phiên bản cũ bất kỳ (Rollback).
- Tự động thay thế biến (Interpolation) và tích hợp mượt mà với AI Task Service khi thực thi tác vụ.

## 1.2. Phạm vi

Tài liệu này quy định việc lưu trữ, quản lý, kiểm tra, mã hóa, cache và cung cấp các câu lệnh System Prompt cho các AI Task Services khác trong dự án `MathClass-service`. Module này chỉ dành riêng cho Administrator; người dùng thông thường không có quyền truy cập.

## 1.3. Giả định

- Hệ thống đã có cơ chế xác thực (Authentication - JWT) và phân quyền (Authorization - Role `ADMIN`).
- Các tác vụ AI (Task) đã được định nghĩa trong hệ thống (như `QUESTION_GEN`, `SUBMISSION_GRADING`, `HINT_EXPLANATION`, `LATEX_CANVAS_FORMAT`).
- Số lượng System Prompt không quá lớn (dưới 500 prompts), do đó có thể cache toàn bộ prompt active vào bộ nhớ (Caffeine Cache).
- Các biến môi trường khả dụng cho từng Task được định nghĩa cố định bởi đội ngũ phát triển và lưu trong cấu hình hệ thống.

---

# 2. Yêu cầu chức năng

## 2.1. Quản lý System Prompt

Administrator có thể thực hiện các thao tác sau đối với câu lệnh điều khiển AI:

| Chức năng | Mô tả |
| :--- | :--- |
| **Danh sách System Prompt** | Xem danh sách tất cả các Prompt trong hệ thống kèm thông tin: ID, Code, Name, Task Code, Allowed Variables, Status (Active/Inactive), Version hiện tại, Created At, Updated At. Cho phép lọc theo `task_code` và tìm kiếm theo `name`/`code`. |
| **Tạo System Prompt** | Tạo mới một câu lệnh với các trường: `code` (duy nhất, viết hoa, phân cách bởi dấu gạch dưới), `name`, `task_code` (FK tham chiếu `task_config`), `default_content` (nội dung gốc), `description`, `allowed_variables` (danh sách biến cho phép). Khi khởi tạo, `current_content` tự động bằng `default_content`. |
| **Sửa System Prompt** | Cập nhật `name`, `current_content`, `description`, `status`. Không được phép thay đổi `code`, `task_code` và `default_content`. Khi chỉnh sửa, hệ thống sẽ thực hiện **Strict Validation** kiểm tra các biến môi trường và tự động tạo bản ghi mới trong bảng Lịch sử (`system_prompt_history`). |
| **Vô hiệu hóa Prompt** | Chuyển `status` thành `INACTIVE`. Prompt bị vô hiệu hóa sẽ không được AI Task Service sử dụng (AI Task Service sẽ fallback về prompt mặc định của hệ thống hoặc báo lỗi). |
| **Kích hoạt Prompt** | Chuyển `status` thành `ACTIVE`. |
| **Xóa System Prompt** | Xóa vĩnh viễn prompt khỏi DB (chỉ xóa được khi không có tác vụ active nào đang bắt buộc sử dụng prompt này). |

## 2.2. Khôi phục mặc định (Reset to Default)

Trong trường hợp Admin chỉnh sửa prompt gây ra lỗi phản hồi AI hoặc làm sai lệch kết quả toán học:

- **Thao tác:** Admin chọn nút **"Khôi phục mặc định"** trên giao diện chi tiết Prompt.
- **Quy trình:**
  1. Backend sao chép nội dung từ trường `default_content` ghi đè vào `current_content`.
  2. Tạo một bản ghi mới trong bảng `system_prompt_history` với số `version` tăng thêm 1 và ghi chú `change_reason = "Khôi phục về bản mặc định gốc"`.
  3. Làm mới Cache (Cache Invalidation) của prompt đó ngay lập tức.
- **Kết quả:** AI Service chuyển sang dùng lại prompt chuẩn ban đầu ngay lập tức mà không cần khởi động lại ứng dụng.

## 2.3. Quản lý Lịch sử & Rollback Phiên bản

Mọi lần chỉnh sửa hoặc khôi phục Prompt đều được lưu trữ đầy đủ trong bảng `system_prompt_history`.

- **Xem lịch sử:** Xem danh sách tất cả các phiên bản cũ của một Prompt gồm: `version`, `content`, `created_by` (email Admin), `created_at`, `change_reason`.
- **So sánh (Diff):** So sánh sự khác biệt giữa hai phiên bản prompt (nhấn mạnh các từ ngữ được thêm/bớt).
- **Rollback:** 
  1. Admin chọn một phiên bản cũ trong lịch sử và nhấn **"Khôi phục phiên bản này"**.
  2. Backend lấy `content` của phiên bản được chọn gán vào `current_content`.
  3. Tạo bản ghi lịch sử mới (version n+1) đánh dấu `change_reason = "Rollback về phiên bản vX"`.
  4. Invalidate cache tương ứng.

## 2.4. Quản lý & Validation Biến môi trường (Variables)

Prompt hỗ trợ chèn các biến môi trường động theo cú pháp: `{{variable_name}}`.

- **Cho phép (Allowed Variables):** Mỗi Prompt định nghĩa sẵn danh sách các biến được hỗ trợ dạng JSON Array (ví dụ: `["grade_level", "subject", "student_answer", "correct_answer"]`).
- **Strict Validation (Kiểm tra nghiêm ngặt khi Lưu):**
  - Khi Admin nhập prompt và chọn Lưu, Backend sử dụng Regular Expression `\{\{([a-zA-Z0-9_]+)\}\}` để trích xuất tất cả các biến xuất hiện trong `current_content`.
  - Nếu tồn tại biến **không nằm trong** `allowed_variables` (ví dụ: gõ nhầm `{{student_answr}}`), Backend sẽ từ chối lưu và trả về lỗi HTTP 400 kèm chi tiết biến vi phạm.
  - Mục đích: Ngăn ngừa rò rỉ dữ liệu nhạy cảm (Security Leak), tránh lỗi gõ sai (Typo) và triệt tiêu nguy cơ AI bị thiếu ngữ cảnh dẫn đến đưa ra kết quả toán học sai lệch (Hallucination).

## 2.5. Engine Thay Thế Biến (Interpolation Engine)

Khi module AI Task gọi Service lấy System Prompt để gửi tới Provider (Gemini/OpenAI):

1. AI Task Service truyền `prompt_code` và `Map<String, Object> variableValues` (ví dụ: `{"grade_level": "Lớp 10", "student_answer": "x = 5"}`).
2. Prompt Service lấy `current_content` từ Cache/DB.
3. Engine duyệt qua các biến trong prompt và thay thế `{{variable_name}}` bằng giá trị thực tế tương ứng.
4. Nếu một biến hợp lệ trong `allowed_variables` nhưng không được truyền giá trị vào `variableValues`, Engine sẽ thay thế bằng chuỗi rỗng `""` và ghi log WARNING.

---

# 3. Yêu cầu phi chức năng

| Yêu cầu | Mô tả |
| :--- | :--- |
| **Bảo mật** | - Kiểm tra quyền Admin nghiêm ngặt cho tất cả các API quản lý.<br>- Strict Validation biến môi trường ngăn chặn Prompt Injection và rò rỉ thông tin nội bộ.<br>- Phân quạt dữ liệu đầu vào (Sanitize input) trước khi lưu vào DB. |
| **Hiệu năng** | - API lấy System Prompt dành cho AI Service execution < 10ms nhờ Cache.<br>- API quản lý Admin < 300ms.<br>- Cache in-memory với Caffeine. |
| **Độ tin cậy** | - Đảm bảo tính nhất quán dữ liệu lịch sử (Transactional khi update prompt và tạo history).<br>- Tự động fallback về `default_content` nếu `current_content` bị rỗng hoặc hỏng. |
| **Audit Log Hệ thống** | Ghi nhật ký đầy đủ tất cả tương tác của Admin (tạo, sửa, đổi trạng thái, reset, rollback, xóa) bao gồm: `username`, `timestamp`, `action`, `target` (promptCode), `oldValue`, `newValue`, `ipAddress` và `changeReason`. |
| **Khả năng mở rộng** | Dễ dàng bổ sung các biến môi trường mới hoặc Task mới mà không cần làm lại cấu trúc cơ sở dữ liệu. |
| **Tính sẵn sàng** | Thay đổi prompt có hiệu lực ngay lập tức trong toàn hệ thống nhờ cơ chế Pub/Sub hoặc Event Invalidate Cache. |

---

# 4. Mô hình dữ liệu

*Ghi chú:* Tất cả các Entity JPA trong dự án kế thừa từ `com.codegym.mathclass.common.entity.BaseEntity` (chứa `id` kiểu `Long` / `BIGSERIAL`, `createdAt`, `updatedAt`).

## 4.1. Bảng `system_prompt`

| Cột | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGSERIAL / UUID | PK | Khóa chính (kế thừa `BaseEntity`) |
| `code` | VARCHAR(50) | NOT NULL, UNIQUE | Mã định danh duy nhất (VD: 'PROMPT_SOLVE_HINT') |
| `name` | VARCHAR(100) | NOT NULL | Tên hiển thị gợi nhớ |
| `task_code` | VARCHAR(50) | NOT NULL | Mã Task AI liên kết (VD: 'QUESTION_GEN', 'SUBMISSION_GRADING') |
| `default_content` | TEXT | NOT NULL | Nội dung prompt gốc do hệ thống thiết lập (Read-only) |
| `current_content` | TEXT | NOT NULL | Nội dung prompt hiện tại đang áp dụng |
| `allowed_variables` | JSONB / TEXT | NOT NULL | Danh sách các biến hợp lệ, VD: `["grade_level","student_answer"]` |
| `description` | VARCHAR(255) | NULL | Mô tả công dụng và cách dùng prompt |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | Trạng thái: 'ACTIVE' hoặc 'INACTIVE' |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm tạo (kế thừa `BaseEntity`) |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm cập nhật lần cuối (kế thừa `BaseEntity`) |

## 4.2. Bảng `system_prompt_history`

| Cột | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | BIGSERIAL / UUID | PK | Khóa chính (kế thừa `BaseEntity`) |
| `prompt_id` | BIGINT / UUID | FK (system_prompt.id), NOT NULL | Prompt liên quan |
| `version` | INT | NOT NULL | Số phiên bản tăng dần (1, 2, 3...) |
| `content` | TEXT | NOT NULL | Nội dung prompt tại phiên bản này |
| `change_reason` | VARCHAR(255) | NULL | Lý do chỉnh sửa / Rollback / Reset |
| `created_by` | VARCHAR(100) | NOT NULL | Email/Username của Admin thực hiện |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm lưu phiên bản (kế thừa `BaseEntity`) |

---

# 5. API Specifications

Tất cả các endpoint dưới đây đều yêu cầu Authentication Header `Authorization: Bearer <JWT>` và có quyền Admin.

## 5.1. Quản lý System Prompt

### 5.1.1. Lấy danh sách System Prompt

- **Endpoint:** `GET /api/v1/system-prompts`
- **Query Parameters:** `taskCode` (tùy chọn), `status` (tùy chọn), `search` (tùy chọn).
- **Response (200 OK):**

```json
{
  "data": [
    {
      "id": 1,
      "code": "PROMPT_SOLVE_HINT",
      "name": "Prompt Gợi ý giải toán từng bước",
      "taskCode": "HINT_EXPLANATION",
      "allowedVariables": ["grade_level", "subject", "student_answer", "question_content"],
      "status": "ACTIVE",
      "description": "Chỉ đưa ra gợi ý hướng giải, tuyệt đối không giải hộ đáp án chi tiết.",
      "createdAt": "2026-08-01T10:00:00Z",
      "updatedAt": "2026-08-06T08:00:00Z"
    }
  ]
}
```

### 5.1.2. Xem chi tiết System Prompt

- **Endpoint:** `GET /api/v1/system-prompts/{id}`
- **Response (200 OK):**

```json
{
  "id": 1,
  "code": "PROMPT_SOLVE_HINT",
  "name": "Prompt Gợi ý giải toán từng bước",
  "taskCode": "HINT_EXPLANATION",
  "defaultContent": "Bạn là giáo viên Toán cho học sinh {{grade_level}}. Khi nhận bài làm {{student_answer}}, hãy chỉ đưa ra gợi ý...",
  "currentContent": "Bạn là trợ lý giảng dạy môn {{subject}} cấp {{grade_level}}. Hãy xem câu hỏi {{question_content}}...",
  "allowedVariables": ["grade_level", "subject", "student_answer", "question_content"],
  "description": "Chỉ đưa ra gợi ý hướng giải, tuyệt đối không giải hộ đáp án chi tiết.",
  "status": "ACTIVE",
  "createdAt": "2026-08-01T10:00:00Z",
  "updatedAt": "2026-08-06T08:00:00Z"
}
```

### 5.1.3. Tạo mới System Prompt

- **Endpoint:** `POST /api/v1/system-prompts`
- **Request Body:**

```json
{
  "code": "PROMPT_LATEX_CANVAS",
  "name": "Prompt Ép chuẩn mã LaTeX / Canvas",
  "taskCode": "LATEX_CANVAS_FORMAT",
  "defaultContent": "Hãy chuyển biểu thức toán sau sang dạng LaTeX chuẩn: {{math_expression}}",
  "allowedVariables": ["math_expression", "output_format"],
  "description": "Đảm bảo AI chỉ trả về block code LaTeX hợp lệ"
}
```

- **Response (201 Created):** Trả về chi tiết Prompt vừa tạo.
- **Lỗi:** `400` – Validation lỗi, `409` – Mã `code` đã tồn tại.

### 5.1.4. Cập nhật System Prompt (Sửa nội dung)

- **Endpoint:** `PUT /api/v1/system-prompts/{id}`
- **Request Body:**

```json
{
  "name": "Prompt Ép chuẩn mã LaTeX / Canvas v2",
  "currentContent": "Bạn là chuyên gia định dạng KaTeX/LaTeX. Yêu cầu chuyển {{math_expression}} sang định dạng {{output_format}}.",
  "description": "Cập nhật tối ưu hóa biểu thức toán phức tạp",
  "status": "ACTIVE",
  "changeReason": "Tối ưu hóa khả năng render căn thức và ma trận"
}
```

- **Response (200 OK):** Trả về DTO của Prompt sau khi cập nhật.
- **Lỗi:** 
  - `400` – Chứa biến môi trường không hợp lệ (Không nằm trong `allowedVariables`).
  - `404` – Không tìm thấy Prompt ID.

### 5.1.5. Khôi phục mặc định (Reset to Default)

- **Endpoint:** `POST /api/v1/system-prompts/{id}/reset`
- **Request Body:** (Trống hoặc tùy chọn lý do)

```json
{
  "reason": "Khôi phục lại bản mặc định sau khi thử nghiệm prompt mới bị lỗi"
}
```

- **Response (200 OK):** Trả về DTO Prompt với `currentContent` đã được khôi phục trùng với `defaultContent`.

## 5.2. Quản lý Lịch sử & Rollback

### 5.2.1. Lấy lịch sử phiên bản Prompt

- **Endpoint:** `GET /api/v1/system-prompts/{id}/history`
- **Response (200 OK):**

```json
{
  "data": [
    {
      "id": 105,
      "promptId": 1,
      "version": 3,
      "content": "Nội dung phiên bản 3...",
      "changeReason": "Khôi phục về bản mặc định gốc",
      "createdBy": "admin@mathclass.edu.vn",
      "createdAt": "2026-08-06T08:15:00Z"
    },
    {
      "id": 98,
      "promptId": 1,
      "version": 2,
      "content": "Nội dung phiên bản 2 thử nghiệm...",
      "changeReason": "Thêm yêu cầu trả về định dạng JSON",
      "createdBy": "admin@mathclass.edu.vn",
      "createdAt": "2026-08-05T14:30:00Z"
    }
  ]
}
```

### 5.2.2. Rollback về một phiên bản cũ

- **Endpoint:** `POST /api/v1/system-prompts/{id}/rollback/{historyId}`
- **Response (200 OK):** Trả về DTO Prompt mới nhất sau khi đã gán `currentContent` bằng nội dung từ bản ghi lịch sử `historyId`.

## 5.3. API dùng nội bộ (Render System Prompt cho AI Service)

- **Endpoint:** `POST /api/v1/system-prompts/render`
- **Request Body:**

```json
{
  "promptCode": "PROMPT_SOLVE_HINT",
  "variables": {
    "grade_level": "Lớp 10",
    "subject": "Đại số",
    "student_answer": "x^2 - 4 = 0 => x = 2",
    "question_content": "Giải phương trình x^2 - 4 = 0"
  }
}
```

- **Response (200 OK):**

```json
{
  "promptCode": "PROMPT_SOLVE_HINT",
  "renderedPrompt": "Bạn là giáo viên Toán cho học sinh Lớp 10 môn Đại số. Khi nhận bài giải Giải phương trình x^2 - 4 = 0 với câu trả lời của học sinh: x^2 - 4 = 0 => x = 2, hãy chỉ đưa ra gợi ý...",
  "usedVariables": ["grade_level", "subject", "student_answer", "question_content"]
}
```

## 5.4. Mã lỗi chung

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `INVALID_VARIABLE` | 400 | Prompt chứa biến môi trường không được phép |
| `PROMPT_NOT_FOUND` | 404 | Prompt ID hoặc Code không tồn tại |
| `PROMPT_CODE_EXISTS` | 409 | Mã prompt code đã tồn tại trong DB |
| `PROMPT_INACTIVE` | 400 | Prompt đang bị vô hiệu hóa |

---

# 6. Chiến lược caching

- Cache thông tin System Prompt (`code`, `current_content`, `allowed_variables`, `status`) bằng Caffeine Cache (in-memory).
- Cache Key: `system_prompt:{promptCode}`.
- TTL (Time To Live): 10 phút.
- **Cache Invalidation:** Tự động xóa cache khi thực hiện các thao tác:
  - Cập nhật prompt (`PUT /api/v1/system-prompts/{id}`).
  - Khôi phục mặc định (`POST /api/v1/system-prompts/{id}/reset`).
  - Rollback phiên bản (`POST /api/v1/system-prompts/{id}/rollback/{historyId}`).
  - Đổi trạng thái `ACTIVE`/`INACTIVE`.

---

# 7. Yêu cầu về logging và giám sát

- **Audit Log Hệ thống (System Audit Log):** Tất cả thao tác quản lý của Administrator đều được tự động ghi vào log kiểm toán hệ thống (`audit_log` / System Logger). Mỗi log bao gồm:
  - `username` / `userId`: Tài khoản Admin thực hiện.
  - `timestamp`: Thời điểm thực hiện hành động.
  - `action`: Loại thao tác (`CREATE_PROMPT`, `UPDATE_PROMPT`, `TOGGLE_PROMPT_STATUS`, `RESET_PROMPT`, `ROLLBACK_PROMPT`, `DELETE_PROMPT`).
  - `target`: Mã Prompt Code hoặc ID chịu tác động.
  - `oldValue`: Nội dung/trạng thái cũ (nếu có).
  - `newValue`: Nội dung/trạng thái mới (nếu có).
  - `ipAddress`: Địa chỉ IP của Admin thực hiện request.
  - `changeReason`: Lý do thay đổi (đối với các thao tác Sửa, Reset, Rollback).
- **Application Log:** Log cảnh báo (WARNING) khi Render prompt mà thiếu giá trị của một biến môi trường hợp lệ.
- **Metrics:** Thống kê số lượt Render Prompt, tỷ lệ hit/miss Cache, danh sách các Prompt được gọi nhiều nhất.

---

# 8. Hướng dẫn triển khai

## 8.1. Công nghệ đề xuất

- **Backend:** Java 21 với Spring Boot 4.x.
- **Database:** PostgreSQL (Hỗ trợ kiểu JSONB lưu `allowed_variables`).
- **Cache:** Caffeine Cache.
- **Template Engine:** Regex Interpolation đơn giản hoặc StringSubstitutor (Apache Commons Text).

## 8.2. Các lớp cần xây dựng

- **Entity:** `SystemPrompt`, `SystemPromptHistory` (kế thừa `BaseEntity`).
- **Repository:** `SystemPromptRepository`, `SystemPromptHistoryRepository`.
- **DTO:** `SystemPromptRequestDTO`, `SystemPromptResponseDTO`, `PromptHistoryResponseDTO`, `RenderPromptRequestDTO`, `RenderPromptResponseDTO`.
- **Mapper:** MapStruct mapper cho SystemPrompt.
- **Validator:** `SystemPromptValidator` (Thực hiện regex parse và validate biến với `allowed_variables`).
- **Service:** `SystemPromptService`, `SystemPromptHistoryService`, `PromptRenderService`, `AuditLogService` (Ghi nhận System Audit Log).
- **Controller:** `SystemPromptController`, `InternalPromptController`.
- **ExceptionHandler:** Xử lý `InvalidVariableException`, `PromptNotFoundException` trong `GlobalExceptionHandler`.

## 8.3. Khởi tạo dữ liệu mẫu (Database Seeding)

Tạo sẵn script Liquibase/Flyway hoặc Migration SQL nạp các System Prompt mặc định ban đầu:
- `PROMPT_SOLVE_HINT` (Task: `HINT_EXPLANATION`)
- `PROMPT_LATEX_CANVAS` (Task: `LATEX_CANVAS_FORMAT`)
- `PROMPT_SUBMISSION_GRADING` (Task: `SUBMISSION_GRADING`)

---

# 9. Kịch bản kiểm tra (Acceptance Criteria)

1. Admin có thể xem danh sách và chi tiết các System Prompt.
2. Admin tạo mới Prompt thành công với `code` duy nhất và biến môi trường hợp lệ.
3. Không thể lưu Prompt nếu chứa biến môi trường nằm ngoài `allowed_variables` (Trả về lỗi `INVALID_VARIABLE`).
4. Khi Admin sửa `current_content`, hệ thống tự động lưu lại phiên bản cũ vào bảng `system_prompt_history` với số version tăng lên 1.
5. Thực hiện "Khôi phục mặc định" sẽ chép `default_content` sang `current_content` và ghi log lịch sử.
6. Admin có thể xem danh sách lịch sử và so sánh khác biệt giữa các phiên bản.
7. Thực hiện Rollback sẽ khôi phục thành công `current_content` từ một bản ghi lịch sử được chọn.
8. API Render tự động thay thế chính xác các cú pháp `{{variable_name}}` thành giá trị thực tế.
9. Mọi thay đổi Prompt (Sửa/Reset/Rollback) đều clear Cache ngay lập tức.
10. Prompt bị `INACTIVE` không cho phép Render sử dụng.
11. Tất cả các thao tác của Admin (tạo, sửa, đổi trạng thái, reset, rollback, xóa) đều ghi nhận đầy đủ bản ghi vào System Audit Log (`username`, `ipAddress`, `action`, `target`, `oldValue`, `newValue`).

---

# 10. Phụ lục – Biểu đồ tuần tự (Luồng Render Prompt khi gọi AI)

```
[AI Service Task] ------------> [PromptRenderService] ------------> [Caffeine Cache]
       |                                |                                   |
       |-- 1. Render(code, vars) ------>|                                   |
       |                                |-- 2. Get prompt by code --------->|
       |                                |<-- 3. Return cached SystemPrompt -|
       |                                |  (If miss: Query DB & Cache it)   |
       |                                |                                   |
       |                                |-- 4. Validate & Interpolate vars -|
       |                                |                                   |
       |<-- 5. Return rendered prompt --|                                   |
       |                                                                    |
       v                                                                    v
[Send Prompt to AI Provider (Gemini/OpenAI)]
```
