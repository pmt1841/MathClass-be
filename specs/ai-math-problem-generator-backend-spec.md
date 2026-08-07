# Specification: AI Math Question & Canvas Generator Backend (`MathClass-service`)

---

## 1. Feature Info
- **Feature Name:** AI Math Question & Canvas Diagram Generator Backend
- **Jira Ticket:** [MAT-251](https://phanvanluan611996.atlassian.net/browse/MAT-251)
- **Target Subsystem:** `MathClass-service` (Backend Microservice - Java 21 / Spring Boot)
- **Target Users:** Teachers (Giáo viên), System Administrators

---

## 2. Business Goal
Cung cấp RESTful API cho phép Giáo viên nhập yêu cầu bằng câu lệnh tự nhiên (Prompt) kết hợp bộ lọc (Khối lớp 6-12, Mức độ tư duy, Chủ đề) để AI tự động tạo bài toán chuẩn KaTeX kèm theo cấu trúc dữ liệu hình vẽ 2D (Canvas JSON Schema). Backend chịu trách nhiệm quản lý prompt, tích hợp với module `KeySelectionService` để giải mã API Key an toàn và gửi request tới AI Provider (Gemini / OpenAI).

---

## 3. Functional Requirements (Yêu cầu Chức năng)

- **FR-1 (AI Generation Request):** Tiếp nhận thông tin yêu cầu tạo bài toán từ Frontend gồm: `prompt` (nội dung câu lệnh), `grade` (khối lớp 6-12), `difficulty` (Mức độ: `NHAN_BIET`, `THONG_HIEU`, `VAN_DUNG`, `VAN_DUNG_CAO`), `topic` (Chủ đề), `questionType` (`ESSAY`, `MULTIPLE_CHOICE`), `includeCanvasDiagram` (boolean).
- **FR-2 (Dynamic API Key Retrieval):** Tích hợp với `KeySelectionService` và `AesGcmEncryptionService` từ module `aiconfig` để lấy API Key active có độ ưu tiên cao nhất, tự động giải mã khóa AES-256-GCM.
- **FR-3 (Structured Prompt Engineering):** Xây dựng System Prompt với quy chuẩn định dạng bắt buộc (JSON Schema Mode). Yêu cầu AI trả về đúng cấu trúc JSON gồm:
  - `title`: Tiêu đề bài toán
  - `content`: Nội dung đề bài dạng Markdown + công thức Toán KaTeX (kẹp trong dấu `$...$` hoặc `$$...$$`)
  - `explanation`: Lời giải chi tiết
  - `canvasData`: Cấu hình danh sách phần tử hình học (`points`, `segments`, `circles`, `angles`, `labels`) để Frontend vẽ lên Canvas.
- **FR-4 (LLM Response Parser & Validator):** Kiểm tra và parse chuỗi JSON trả về từ LLM. Nếu AI trả về format lỗi hoặc vỡ JSON, ném ngoại lệ `AiResponseParseException` và tự động retry 1 lần.
- **FR-5 (Quota & Failover Handling):** Nếu gọi Provider chính bị lỗi `429 Too Many Requests` hoặc `401 Unauthorized`, tự động chuyển trạng thái Key trong DB và retry với Key/Provider dự phòng.

---

## 4. Business Rules (Quy tắc Nghiệp vụ)

- **BR-1 (Secure Key Management):** Tuyệt đối không hardcode API Key hoặc lưu API Key dạng Plaintext trong mã nguồn hay log file. Mọi thao tác đều phải dùng `KeySelectionService`.
- **BR-2 (Strict JSON Output):** AI phải trả về dữ liệu tuân thủ đúng DTO `AiGeneratedQuestionDTO`. Các ký tự đặc biệt trong KaTeX (như `\frac`, `\sqrt`, `\vec`) phải được escape đúng chuẩn JSON string (`\\frac`, `\\sqrt`).
- **BR-3 (Response Envelope Standard):** Mọi API Response đều bọc trong đối tượng `ApiResponse<T>` chuẩn của dự án MathClass.

---

## 5. Package Structure & Architectural Components

```
com.codegym.mathclass.ai/
├── controller/
│   └── AiQuestionController.java           # REST Controller nhận request
├── service/
│   ├── AiQuestionService.java              # Interface nghiệp vụ sinh câu hỏi
│   └── impl/
│       └── AiQuestionServiceImpl.java      # Implementation (gọi KeySelectionService & RestTemplate/WebClient)
├── dto/
│   ├── request/
│   │   └── GenerateQuestionRequestDTO.java # Request DTO
│   └── response/
│       ├── AiGeneratedQuestionDTO.java     # Response DTO chính
│       ├── CanvasDataDTO.java              # Schema tổng của Canvas 2D
│       └── CanvasElementDTO.java           # Schema từng phần tử hình học
└── exception/
    └── AiGenerationException.java          # Custom Exception cho module AI
```

---

## 6. Data Transfer Objects (DTO Schemas)

### 6.1. `GenerateQuestionRequestDTO`
```java
package com.codegym.mathclass.ai.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateQuestionRequestDTO {
    @NotBlank(message = "Prompt không được để trống")
    @Size(max = 2000, message = "Prompt tối đa 2000 ký tự")
    private String prompt;

    @Min(value = 6, message = "Khối lớp từ 6 đến 12")
    @Max(value = 12, message = "Khối lớp từ 6 đến 12")
    private Integer grade;

    private String difficulty; // NHAN_BIET, THONG_HIEU, VAN_DUNG, VAN_DUNG_CAO

    private String topic;

    private Boolean includeCanvasDiagram = true;
}
```

### 6.2. `AiGeneratedQuestionDTO`
```java
package com.codegym.mathclass.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGeneratedQuestionDTO {
    private String title;
    private String content;      // KaTeX math content
    private String explanation;  // Lời giải chi tiết
    private Integer grade;
    private String difficulty;
    private String topic;
    private CanvasDataDTO canvasData;
}
```

### 6.3. `CanvasDataDTO` & `CanvasElementDTO`
```java
package com.codegym.mathclass.ai.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class CanvasDataDTO {
    private Integer width = 500;
    private Integer height = 400;
    private List<CanvasElementDTO> elements;
}
```

```java
package com.codegym.mathclass.ai.dto.response;

import lombok.Data;

@Data
public class CanvasElementDTO {
    private String type;          // "point", "segment", "circle", "angle"
    private String id;
    private Double x;             // Tọa độ X (dành cho point)
    private Double y;             // Tọa độ Y (dành cho point)
    private String label;         // Nhãn (vd: "A", "B", "O")
    private String labelPosition; // "top", "bottom", "left", "right", "top-left"...
    private String centerId;      // Id điểm tâm (dành cho circle)
    private Double radius;        // Bán kính (dành cho circle)
    private String fromId;        // Id điểm bắt đầu (dành cho segment)
    private String toId;          // Id điểm kết thúc (dành cho segment)
    private String style;         // "solid", "dashed"
}
```

---

## 7. REST API Endpoint Specification

### `POST /api/v1/ai/generate-question`
- **Security:** Yêu cầu JWT Token (`@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")`)
- **Content-Type:** `application/json`

#### Response ví dụ (`200 OK`):
```json
{
  "code": 1000,
  "message": "Sinh câu hỏi AI thành công",
  "result": {
    "title": "Bài toán Đường tròn nội tiếp tam giác",
    "content": "Cho tam giác $ABC$ nhọn nội tiếp đường tròn $(O; R)$. Vẽ đường cao $AH$...",
    "explanation": "Lời giải chi tiết từng bước...",
    "grade": 9,
    "difficulty": "THONG_HIEU",
    "topic": "Hình học 9 - Đường tròn",
    "canvasData": {
      "width": 500,
      "height": 400,
      "elements": [
        { "type": "point", "id": "O", "x": 250.0, "y": 200.0, "label": "O", "labelPosition": "top-left" },
        { "type": "circle", "id": "c1", "centerId": "O", "radius": 120.0, "style": "solid" },
        { "type": "point", "id": "A", "x": 250.0, "y": 80.0, "label": "A", "labelPosition": "top" },
        { "type": "point", "id": "B", "x": 145.0, "y": 260.0, "label": "B", "labelPosition": "bottom-left" },
        { "type": "point", "id": "C", "x": 355.0, "y": 260.0, "label": "C", "labelPosition": "bottom-right" },
        { "type": "segment", "id": "s1", "fromId": "A", "toId": "B", "style": "solid" },
        { "type": "segment", "id": "s2", "fromId": "B", "toId": "C", "style": "solid" },
        { "type": "segment", "id": "s3", "fromId": "C", "toId": "A", "style": "solid" }
      ]
    }
  }
}
```

---

## 8. Verification Checklist (Backend)

Sau khi hoàn thành code Backend:
```bash
# 1. Kiểm tra biên dịch Java
./gradlew compileJava

# 2. Chạy Unit Test
./gradlew test
```
