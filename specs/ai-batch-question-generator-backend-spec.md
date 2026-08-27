# Specification: AI Batch Question Generator Backend (`MathClass-service`)

---

## 1. Feature Info
- **Feature Name:** AI Batch Question Generator from Files & Text Backend (AI Tách Đề Thi Hàng Loạt)
- **Jira Ticket:** [MAT-332](https://phanvanluan611996.atlassian.net/browse/MAT-332)
- **Target Subsystem:** `MathClass-service` (Backend Microservice - Java 21 / Spring Boot 3.4+)
- **Target Users:** Teachers (Giáo viên), System Administrators

---

## 2. Business Goal
Cung cấp RESTful API và Service cho phép Giáo viên tải lên tài liệu đề thi dạng file (Microsoft Word `.docx`, PDF `.pdf`, Text `.txt`) hoặc dán trực tiếp nội dung đề bài thô. Hệ thống Backend tự động trích xuất nội dung văn bản và ảnh nhúng, tích hợp với AI LLM (Gemini 2.0 / OpenAI) thông qua Prompt Engine để bóc tách từng bài toán/câu hỏi thành các bài tập bản nháp (`DRAFT`) độc lập. Ngoài ra, Backend cung cấp API Batch Creation để lưu đồng loạt các bài tập này vào Kho bài tập cá nhân của giáo viên một cách an toàn và tối ưu giao dịch (`@Transactional`).

---

## 3. Functional Requirements (Yêu cầu Chức năng)

- **FR-1 (File Extraction & Document Ingestion):** Tiếp nhận file tài liệu (`.docx`, `.pdf`, `.txt`) tối đa 15MB hoặc chuỗi `textContent`. Tái sử dụng `AssignmentService.extractTextFromFile()` để bóc tách text và danh sách ảnh nhúng (`AssignmentImageDto` với `imageCode` và `imageUrl`).
- **FR-2 (Dynamic Key Selection & Failover):** Tích hợp với `KeySelectionService` và `TaskConfigRepository` để tìm Provider/Model tương ứng cho task `BATCH_QUESTION_GEN` (fallback `QUESTION_GEN`). Tự động xoay vòng API Key active, bắt mã lỗi `401` để vô hiệu hóa key, bắt mã `429` để kích hoạt cooldown key 300s và thử key tiếp theo.
- **FR-3 (Structured AI Prompt Rendering):** Tích hợp với `PromptRenderService` nạp mẫu prompt `PROMPT_BATCH_QUESTION_GEN` từ cơ sở dữ liệu (`DatabaseSeeder`), truyền các biến ngữ cảnh (`grade_level`, `topic`, `document_content`, `explanation_requirement`, `canvas_requirement`).
- **FR-4 (Strict KaTeX & JSON Parsing):** Xử lý và parse JSON từ LLM phản hồi. Tự động chuẩn hóa các ký tự thoát KaTeX (`\text{}`, `\frac{}`, `\sqrt{}`) và định dạng lại các thẻ công thức toán qua `LaTeXSanitizer.normalizeKatexDelimiters()`.
- **FR-5 (AI Credit Lifecycle Management):** Trừ credit tự động đối với giáo viên (2 Credits/lượt tạo):
  - `reserve(userId, "BATCH_QUESTION_GEN", 2)` trước khi gọi LLM.
  - `settle(...)` sau khi sinh câu hỏi thành công dựa trên số token thực tế.
  - `refund(...)` tự động nếu quá trình trích xuất hoặc gọi AI thất bại (đảm bảo an toàn trong khối `try-catch-finally`).
  - Miễn phí cho người dùng có quyền `ADMIN`.
- **FR-6 (Batch Assignment Persistence):** Cung cấp endpoint `POST /api/v1/assignments/batch` nhận danh sách `CreateAssignmentRequest` để lưu $N$ bài tập vào CSDL trong một transaction duy nhất.

---

## 4. Business Rules (Quy tắc Nghiệp vụ)

- **BR-1 (Secure Key & Zero Leakage):** Tuyệt đối không hardcode API Key hoặc ghi log Plaintext API Key / Header Authorization. Mọi thao tác đều phải thông qua `KeySelectionService` và `AiProviderStrategyFactory`.
- **BR-2 (Strict Math Syntax):** Tất cả các công thức toán học, đại lượng, ký hiệu biến số ($x, y, \pi, \alpha$), đơn vị đo lường ($cm, cm^2$) trong đề bài phải được bọc trong dấu đô-la `$ ... $` (inline math) hoặc `$$ ... $$` (block math).
- **BR-3 (Independent Draft Assignments):** Mỗi bài toán được bóc tách từ tài liệu khi lưu vào hệ thống là một bản ghi `Assignment` độc lập với trạng thái `DRAFT`, sẵn sàng cho giáo viên chỉnh sửa, gán tag, hoặc phát hành (Publish) tới các lớp học sau này.
- **BR-4 (Atomic Transactions):** Nghiệp vụ lưu hàng loạt bài tập sử dụng `@Transactional` để đảm bảo tính toàn vẹn dữ liệu.
- **BR-5 (Credit Quota):** Task `BATCH_QUESTION_GEN` có định mức mặc định `costPerCall = 2` credits và `tokensPerCredit = 1000`.

---

## 5. Package Structure & Architectural Components

```
com.codegym.mathclass/
├── aiconfig/
│   ├── controller/
│   │   └── AiFeatureController.java             # Quản lý danh sách Feature Tasks
│   └── credit/service/impl/
│       └── AiCreditServiceImpl.java             # Mapping nhãn & khấu trừ Credit
├── assignment/
│   ├── controller/
│   │   ├── AiQuestionController.java            # REST Controller endpoint /api/v1/ai
│   │   └── AssignmentController.java            # REST Controller endpoint /api/v1/assignments/batch
│   ├── dto/
│   │   ├── BatchGenerateQuestionsRequest.java   # Request DTO (Multipart / ModelAttribute)
│   │   ├── BatchGenerateQuestionsResponse.java  # Response DTO tổng thể
│   │   └── BatchQuestionItem.java               # DTO đại diện cho từng câu hỏi lẻ
│   ├── service/
│   │   ├── AiBatchQuestionService.java          # Interface nghiệp vụ AI Tách đề
│   │   ├── AssignmentService.java               # Interface bổ sung createBatchAssignments()
│   │   └── impl/
│   │       ├── AiBatchQuestionServiceImpl.java  # Core Implementation bóc tách đề & gọi AI
│   │       └── AssignmentServiceImpl.java       # Implementation lưu hàng loạt bản nháp
│   └── exception/
│       └── AiGenerationException.java           # Ngoại lệ chung khi AI xử lý thất bại
└── config/
    └── DatabaseSeeder.java                      # Khởi tạo Credit Config & System Prompt mẫu
```

---

## 6. Data Transfer Objects (DTO Schemas)

### 6.1. `BatchGenerateQuestionsRequest`
```java
package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerateQuestionsRequest {
    private MultipartFile file;
    private String textContent;
    private Integer grade;
    private String topic;
    private String questionType;
    private Boolean includeExplanation;
    private Boolean includeCanvasDiagram;
}
```

### 6.2. `BatchGenerateQuestionsResponse`
```java
package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerateQuestionsResponse {
    private String suggestedTitle;
    private String suggestedDescription;
    private List<BatchQuestionItem> questions;
    private int totalQuestions;
    private List<AssignmentImageDto> extractedImages;
    private String model;
}
```

### 6.3. `BatchQuestionItem`
```java
package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchQuestionItem {
    private String id;
    private String title;
    private String description;
    private String content;
    private String explanation;
    private String difficulty;
    private Double suggestedScore;
}
```

---

## 7. REST API Endpoints Specification

### 7.1. `POST /api/v1/ai/batch-generate-questions`
- **Mục đích:** Tải file tài liệu hoặc gửi văn bản đề thi thô để AI bóc tách thành danh sách bài tập.
- **Security:** `@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') or hasAuthority('assignment:create')")`
- **Content-Type:** `multipart/form-data`

#### Request Parameters:
| Tên tham số | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :--- | :--- | :--- | :--- |
| `file` | `MultipartFile` | Không | File Word (`.docx`), PDF (`.pdf`), Text (`.txt`) tối đa 15MB |
| `textContent` | `String` | Không | Nội dung văn bản đề thi dán trực tiếp |
| `grade` | `Integer` | Không | Khối lớp (6-12), mặc định 9 |
| `topic` | `String` | Không | Chủ đề / chuyên đề toán |
| `includeExplanation` | `Boolean` | Không | Yêu cầu sinh lời giải (mặc định `false`) |
| `includeCanvasDiagram` | `Boolean` | Không | Yêu cầu sinh dữ liệu Canvas (mặc định `false`) |

#### Response (`200 OK`):
```json
{
  "suggestedTitle": "Đề thi thử vào lớp 10 môn Toán",
  "suggestedDescription": "Đề thi gồm 3 bài tập đại số và hình học",
  "questions": [
    {
      "id": "q1",
      "title": "Bài 1: Rút gọn biểu thức chứa căn",
      "content": "Cho biểu thức $A = \\frac{\\sqrt{x}+1}{\\sqrt{x}-1}$ với $x \\ge 0, x \\ne 1$...",
      "explanation": "",
      "difficulty": "THONG_HIEU",
      "suggestedScore": 2.0
    },
    {
      "id": "q2",
      "title": "Bài 2: Giải bài toán bằng cách lập hệ phương trình",
      "content": "Một mảnh vườn hình chữ nhật có chu vi là $40\\text{ m}$...",
      "explanation": "",
      "difficulty": "VAN_DUNG",
      "suggestedScore": 2.5
    }
  ],
  "totalQuestions": 2,
  "extractedImages": [],
  "model": "gemini-2.0-flash"
}
```

---

### 7.2. `POST /api/v1/assignments/batch`
- **Mục đích:** Tạo đồng loạt nhiều bài tập độc lập (bản nháp `DRAFT`) vào Kho bài tập của giáo viên.
- **Security:** `@PreAuthorize("hasAuthority('assignment:create')")`
- **Content-Type:** `application/json`

#### Request Body (`List<CreateAssignmentRequest>`):
```json
[
  {
    "title": "Bài 1: Rút gọn biểu thức chứa căn",
    "content": "Cho biểu thức $A = \\frac{\\sqrt{x}+1}{\\sqrt{x}-1}$...",
    "allowResubmit": true,
    "images": []
  },
  {
    "title": "Bài 2: Giải bài toán bằng cách lập hệ phương trình",
    "content": "Một mảnh vườn hình chữ nhật...",
    "allowResubmit": true,
    "images": []
  }
]
```

#### Response (`201 Created`):
```json
[
  {
    "id": 105,
    "title": "Bài 1: Rút gọn biểu thức chứa căn",
    "content": "Cho biểu thức $A = \\frac{\\sqrt{x}+1}{\\sqrt{x}-1}$...",
    "status": "DRAFT",
    "createdAt": "2026-08-27T08:30:00Z"
  },
  {
    "id": 106,
    "title": "Bài 2: Giải bài toán bằng cách lập hệ phương trình",
    "content": "Một mảnh vườn hình chữ nhật...",
    "status": "DRAFT",
    "createdAt": "2026-08-27T08:30:00Z"
  }
]
```

---

## 8. Verification Checklist (Backend Testing)

```bash
# 1. Biên dịch toàn bộ mã nguồn Java
./gradlew compileJava

# 2. Chạy Unit Tests cho Controller và Service của tính năng
./gradlew test --tests "com.codegym.mathclass.assignment.service.AiBatchQuestionServiceImplTest" --tests "com.codegym.mathclass.assignment.controller.AiQuestionControllerTest"

# 3. Chạy toàn bộ test suite của hệ thống
./gradlew test
```
