# Specification: Student Step-by-Step AI Thinking Hint (`MathClass-service` & `MathClass-fe`)

---

## 1. Feature Overview
- **Feature Name:** Student Step-by-Step AI Thinking Hint (Gợi ý tư duy từng bước cho Học sinh)
- **Jira Ticket:** [MAT-249](https://phanvanluan611996.atlassian.net/browse/MAT-249)
- **Target Subsystems:** `MathClass-service` (Backend Microservice), `MathClass-fe` (Next.js Frontend)
- **Target Users:** Student (Học sinh làm bài), Teacher (Giáo viên theo dõi mức độ tự lực)

---

## 2. Business Goal & Core Objectives
Cung cấp nút **"Cần gợi ý 💡"** ngay tại giao diện làm bài của Học sinh. Khi bấm nút, AI sẽ phân tích Đề bài (Văn bản, KaTeX, Mô tả hình vẽ) cùng Tiến độ bài làm hiện tại của Học sinh để đưa ra **01 gợi ý tư duy định hướng bước tiếp theo**. 

Hệ thống bắt buộc phải tuân thủ các nguyên tắc:
1. **Không cho đáp án trực tiếp / Không giải hộ hoàn toàn** (Socratic Method).
2. **Giới hạn số lần gợi ý:** Tối đa 3 gợi ý / 1 bài tập.
3. **Lưu nhật ký gợi ý (Hint History):** Lưu vết đầy đủ các gợi ý Học sinh đã nhận để Giáo viên theo dõi mức độ tự lực khi chấm bài.
4. **Sử dụng phân hệ AI Services sẵn có:** Tích hợp với `ai_task_configs` (task_code: `STUDENT_HINT`), hỗ trợ dynamic key selection, multi-provider failover và AES-256 key encryption.

---

## 3. Potential Logic Loopholes & Mitigations (5 Key Edge Cases)

### 3.1. Case 1: Lỗi Race Condition đếm lượt khi Submission chưa tồn tại
- **Vấn đề:** Khi Học sinh chưa bấm "Lưu nháp" lần nào, `submission_id` chưa tồn tại trong DB. Nếu Học sinh bấm nút "Cần gợi ý" 2 lần liên tiếp thật nhanh, 2 request song song gửi lên Backend sẽ cùng không tìm thấy Submission và cùng tạo ra 2 bản ghi `Submission` hoặc tạo duplicate `SubmissionHint` với cùng `hint_number = 1`.
- **Khắc phục:** Khi Học sinh mở bài tập hoặc trước khi xin gợi ý, Backend bảo đảm khởi tạo/tìm `Submission` ở trạng thái `DRAFT`. API gợi ý sử dụng Pessimistic Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) trên bản ghi `Submission` để đếm và tạo `SubmissionHint` đồng bộ.

### 3.2. Case 2: Lỗi Phạt trừ lượt khi hệ thống AI gặp sự cố (System Fault Penalty)
- **Vấn đề:** Nếu Backend tăng lượt gợi ý từ 1 -> 2 rồi mới gọi API tới AI Provider (Gemini/OpenAI), nhưng request AI bị Timeout (504), Rate Limit (429) hoặc Lỗi server AI (500), Học sinh sẽ bị mất 1 lượt gợi ý mà không nhận được thông tin gì.
- **Khắc phục:** Thực hiện Transaction 2 pha: Gọi AI thu về kết quả gợi ý thành công `200 OK` **rồi mới** lưu bản ghi `SubmissionHint` và tăng số lượt đếm xuống CSDL. Nếu AI lỗi, nếm ngoại lệ phù hợp và **không ghi nhận lượt dùng**.

### 3.3. Case 3: Lỗi Lộ đáp án mẫu khi Đề bài có chứa Lời giải mẫu trong CSDL
- **Vấn đề:** Trong bảng `assignments` có trường `sample_solution` (lời giải tham khảo của Giáo viên). Nếu Backend sơ suất đưa nguyên văn `sample_solution` vào Prompt gửi cho AI mà không có chỉ thị chặn khắt khe, AI có thể chép lại kết quả cuối cùng cho Học sinh.
- **Khắc phục:** 
  1. Tuyệt đối **không truyền `sample_solution`** vào Prompt nếu không cần thiết, hoặc chỉ truyền dưới dạng `guidance_steps` không có kết quả số.
  2. Bắt buộc có Guardrail System Prompt chặn output đáp số: *"Nếu phát hiện câu trả lời chứa kết quả số cuối cùng hoặc bài giải hoàn chỉnh, hãy loại bỏ và chỉ giữ lại câu hỏi gợi mở."*

### 3.4. Case 4: Lỗi Trôi ngữ cảnh gợi ý khi Học sinh xóa/thay đổi hướng làm bài (Hint Drift)
- **Vấn đề:** Học sinh nhận Gợi ý #1 cho Hướng giải A. Sau đó Học sinh xóa hết bài làm, chuyển sang Hướng giải B và bấm xin Gợi ý #2. Nếu AI đọc lại Lịch sử Gợi ý #1 (thuộc Hướng A), AI sẽ bị nhiễu ngữ cảnh và đưa ra gợi ý mâu thuẫn với Hướng B.
- **Khắc phục:** Khi xây dựng Prompt cho AI, Lịch sử Gợi ý cũ chỉ đóng vai trò thông tin tham khảo. AI được yêu cầu ưu tiên phân tích **Nội dung bài làm hiện tại** (`current_student_content`) làm căn cứ chính để đưa ra bước tiếp theo.

### 3.5. Case 5: Lỗi Bùng nổ Token/Chi phí do Copy-Paste khối văn bản lớn hoặc Hình vẽ JSXGraph
- **Vấn đề:** Học sinh copy đoạn văn bản rất dài hoặc khối JSON JSXGraph khổng lồ (`<!-- DRAWINGS_DATA_START ... -->`) vào bài làm khiến Payload gửi cho AI vượt giới hạn `max_tokens` của Model, gây lỗi HTTP 400/413 hoặc tốn chi phí khủng khép.
- **Khắc phục:** 
  1. Loại bỏ các đoạn JSON thô `DRAWINGS_DATA_START...DRAWINGS_DATA_END` trước khi gửi vào Prompt. Thay thế bằng nhãn tóm tắt: `[Hình vẽ/Đồ thị JSXGraph: SHAPE_1]`.
  2. Cắt ngắn nội dung bài làm của Học sinh tối đa 3,000 ký tự gần nhất trước khi gửi cho AI.

---

## 4. Functional Requirements

- **FR-1 (Trigger Hint):** Cho phép Học sinh bấm nút "Cần gợi ý" trên UI làm bài.
- **FR-2 (Quota Check & Enforce):** Kiểm tra giới hạn (tối đa 3 gợi ý/bài tập). Nếu đã dùng hết 3/3 lượt, nút bị disable và trả về lỗi `400 Bad Request` nếu cố tình gọi API.
- **FR-3 (AI Dynamic Task Integration):** Sử dụng cấu hình `STUDENT_HINT` từ bảng `ai_task_configs`. Tự động lấy Provider, Model, Temperature (khuyên dùng `0.3 - 0.5` cho định hướng chính xác) và API Key hoạt động.
- **FR-4 (Hint History Recording):** Lưu trữ toàn bộ lịch sử các lần xin gợi ý vào bảng `submission_hints`.
- **FR-5 (Teacher Visibility):** Khi Giáo viên xem/chấm bài làm của Học sinh (`Submission`), hiển thị danh sách các gợi ý Học sinh đã bấm kèm mốc thời gian và bài làm tại thời điểm bấm.

---

## 5. Business Rules

- **BR-1 (Socratic Method Strict Constraint):** Gợi ý AI trả về không quá 150 từ, tập trung vào 1 bước tư duy kế tiếp, câu hỏi gợi mở hoặc nhắc lại công thức/định lý liên quan. Tuyệt đối không cho đáp số.
- **BR-2 (Read-Only Prohibition):** Không cho phép học sinh xin gợi ý khi bài nộp đã ở trạng thái `SUBMITTED`, `GRADED` hoặc khi bài tập đã quá hạn `deadline`.
- **BR-3 (Non-Penalty Fault Rule):** Khi request gọi AI bị thất bại do sự cố mạng/AI Provider, hệ thống không tính trừ số lượt gợi ý còn lại của học sinh.
- **BR-4 (Rate Limit):** Mỗi lần bấm gợi ý phải cách nhau tối thiểu 10 giây (Anti-spam).

---

## 6. Data Model

### 6.1. Update `ai_task_configs` Data Seed
Thêm 1 record mặc định vào bảng `ai_task_configs`:
- `task_code`: `STUDENT_HINT`
- `task_name`: `Gợi ý tư duy làm bài cho Học sinh`
- `temperature`: `0.4`
- `max_tokens`: `512`

### 6.2. Entity `SubmissionHint` (`submission_hints`)
- **Java Class:** `com.codegym.mathclass.submission.entity.SubmissionHint extends BaseEntity`

```sql
CREATE TABLE submission_hints (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id),
    hint_number INT NOT NULL, -- 1, 2, hoặc 3
    student_snapshot_content TEXT, -- Ảnh chụp nội dung bài làm của học sinh tại thời điểm xin gợi ý
    ai_hint_content TEXT NOT NULL, -- Nội dung gợi ý do AI sinh ra
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_submission_hint_number UNIQUE (submission_id, hint_number)
);

CREATE INDEX idx_submission_hints_sub_id ON submission_hints(submission_id);
```

---

## 7. API Contract

### 7.1. Request Step-by-Step AI Hint
- **HTTP Method:** `POST`
- **Path:** `/api/v1/submissions/{assignmentId}/hints`
- **Authorization:** `@PreAuthorize("hasRole('STUDENT')")`
- **Request Body:**
```json
{
  "currentContent": "Ta có phương trình x^2 - 5x + 6 = 0. Em đã tính delta = (-5)^2 - 4*1*6 = 1 > 0."
}
```

- **Response `200 OK` (Thành công):**
```json
{
  "code": 200,
  "message": "Tạo gợi ý thành công",
  "data": {
    "id": 15,
    "submissionId": 102,
    "hintNumber": 1,
    "maxHints": 3,
    "remainingHints": 2,
    "hintContent": "Vì Delta = 1 > 0, phương trình sẽ có 2 nghiệm phân biệt. Hãy nhớ lại công thức tính nghiệm x1, x2 theo b và sqrt(Delta) để tiếp tục tính toán.",
    "createdAt": "2026-08-05T15:00:00"
  }
}
```

- **Response `400 Bad Request` (Đã dùng hết 3 lượt):**
```json
{
  "code": 400,
  "message": "Bạn đã sử dụng tối đa 3/3 lượt gợi ý cho bài tập này.",
  "errorCode": "HINT_LIMIT_EXCEEDED"
}
```

---

### 7.2. Get Hint History for a Submission
- **HTTP Method:** `GET`
- **Path:** `/api/v1/submissions/{submissionId}/hints`
- **Authorization:** `@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")`
- **Response `200 OK`:**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "submissionId": 102,
    "totalUsed": 2,
    "maxHints": 3,
    "hints": [
      {
        "hintNumber": 1,
        "studentSnapshotContent": "Ta có phương trình x^2 - 5x + 6 = 0...",
        "aiHintContent": "Vì Delta = 1 > 0, phương trình sẽ có 2 nghiệm phân biệt...",
        "createdAt": "2026-08-05T15:00:00"
      },
      {
        "hintNumber": 2,
        "studentSnapshotContent": "x1 = (5 + 1)/2 = 3...",
        "aiHintContent": "Hãy kiểm tra lại bước tính x2 với dấu âm trước căn delta...",
        "createdAt": "2026-08-05T15:05:00"
      }
    ]
  }
}
```

---

## 8. Prompt Engineering Strategy

### System Prompt (`STUDENT_HINT`)
```text
Bạn là một trợ lý giáo viên Toán học xuất sắc và kiên nhẫn.
Nhiệm vụ của bạn là đưa ra 01 GỢI Ý TƯ DUY NGẮN (không quá 120 từ) giúp học sinh tự thực hiện BƯỚC TIẾP THEO trong bài tập.

CÁC QUY TẮC BẮT BUỘC:
1. TUYỆT ĐỐI KHÔNG cung cấp kết quả số cuối cùng hoặc giải hoàn chỉnh bài toán.
2. Nối tiếp đúng theo tiến độ bài làm hiện tại của học sinh. Nếu học sinh làm sai ở đâu, hãy nhẹ nhàng chỉ ra vị trí cần kiểm tra lại.
3. Nếu bài làm của học sinh đang trống, hãy đưa ra hướng tiếp cận ban đầu (Xác định giả thiết, công thức cần dùng).
4. Sử dụng ngôn ngữ thân thiện, động viên, viết công thức toán dưới dạng KaTeX ngắn gọn (ví dụ $x^2 + 1$).
```

---

## 9. Verification & Testing Strategy

- **Unit Tests:** 
  - Test logic đếm `hint_number` đảm bảo không vượt quá 3.
  - Test trường hợp `currentContent` rỗng/chứa hình vẽ JSXGraph.
- **Integration Tests:** 
  - Test API `POST /api/v1/submissions/{assignmentId}/hints` kiểm tra phân quyền Student & Lock bảo mật.
  - Test fallback AI Provider khi Provider chính trả về lỗi 429.

---

## 10. Acceptance Criteria Checklist (Tiêu chuẩn Nghiệm thu)

- [ ] **AC-1 (Happy Path - Lượt gợi ý đầu tiên):**
  - **Given** Học sinh đang ở trang làm bài tập chưa nộp và chưa dùng gợi ý nào (`0/3`).
  - **When** Học sinh bấm nút "Cần gợi ý 💡".
  - **Then** Hệ thống gửi tiến độ bài làm lên AI, trả về 1 đoạn gợi ý định hướng ngắn gọn và cập nhật badge lượt thành `1/3` (còn 2 lượt).

- [ ] **AC-2 (Giới hạn tối đa 3 gợi ý):**
  - **Given** Học sinh đã sử dụng `3/3` lượt gợi ý trong bài tập này.
  - **When** Học sinh xem lại giao diện hoặc cố tình gửi request `POST /hints`.
  - **Then** Nút "Cần gợi ý" hiển thị trạng thái disabled với tooltip "Đã dùng hết 3/3 lượt gợi ý", API trả về lỗi `400 Bad Request` với `errorCode: HINT_LIMIT_EXCEEDED`.

- [ ] **AC-3 (Tự động tạo DRAFT Submission):**
  - **Given** Học sinh vừa vào bài tập, chưa từng bấm "Lưu nháp" (`submissionId = null`).
  - **When** Học sinh bấm "Cần gợi ý".
  - **Then** Backend tự động khởi tạo bản ghi `Submission` với trạng thái `DRAFT` và lưu vết `SubmissionHint` đầu tiên.

- [ ] **AC-4 (Không bị trừ lượt khi AI lỗi - Non-Penalty Fault Rule):**
  - **Given** Học sinh bấm "Cần gợi ý" khi đã dùng `1/3` lượt.
  - **When** Provider AI phản hồi lỗi `429 Too Many Requests`, `500 Internal Error` hoặc `Timeout`.
  - **Then** Hệ thống hiển thị thông báo lỗi thân thiện, **không ghi nhận thêm hint** và giữ nguyên số lượt đã dùng là `1/3`.

- [ ] **AC-5 (Khóa gợi ý khi bài tập đã nộp hoặc quá hạn):**
  - **Given** Bài nộp đang ở trạng thái `SUBMITTED`, `GRADED` hoặc đã quá `deadline`.
  - **When** Học sinh xem bài nộp.
  - **Then** Nút "Cần gợi ý" bị ẩn/khóa, API trả về lỗi `400 Bad Request` nếu gọi trực tiếp.

- [ ] **AC-6 (Không lộ đáp số cuối cùng):**
  - **Given** AI sinh phản hồi từ bài làm hiện tại của học sinh.
  - **When** Kết quả được trả về Frontend.
  - **Then** Phản hồi chỉ chứa bước gợi ý tư duy kế tiếp, công thức gợi mở, tuyệt đối không chứa đáp số số học cuối cùng.

- [ ] **AC-7 (Giáo viên xem Lịch sử Gợi ý):**
  - **Given** Giáo viên mở giao diện chấm bài làm của Học sinh.
  - **When** Quan sát thông tin chi tiết bài nộp.
  - **Then** Giáo viên thấy được Tab/Khung "Lịch sử gợi ý AI" hiển thị các mốc thời gian Học sinh đã xin gợi ý và nội dung gợi ý AI đã trả về.

---

## 11. Unit & Integration Test Cases Checklist

### 11.1. Backend Unit Tests (`SubmissionHintServiceTest.java`)

- [ ] **UT-BE-01:** `requestHint_happyPath_firstHint_shouldReturnHintAndSetCountToOne()`
  - Kiểm tra trường hợp chuẩn: Học sinh gửi nội dung bài làm hợp lệ $\rightarrow$ Trả về `hintNumber = 1`, `remainingHints = 2`.
- [ ] **UT-BE-02:** `requestHint_submissionNotExists_shouldAutoCreateDraftSubmission()`
  - Kiểm tra khi chưa có Submission $\rightarrow$ Tự động tạo `Submission(status=DRAFT)` và tạo `SubmissionHint(hintNumber=1)`.
- [ ] **UT-BE-03:** `requestHint_reachedMax3Hints_shouldThrowHintLimitExceededException()`
  - Kiểm tra khi DB đã có 3 `SubmissionHint` $\rightarrow$ Ném `HintLimitExceededException` (HTTP 400).
- [ ] **UT-BE-04:** `requestHint_aiProviderError_shouldNotDeductQuota()`
  - Mock `KeySelectionService` hoặc LLM Call ném `AiServiceException` $\rightarrow$ Verify `submissionHintRepository.save()` KHÔNG được gọi.
- [ ] **UT-BE-05:** `requestHint_submittedStatus_shouldThrowIllegalStateException()`
  - Khi `submission.getStatus() == SUBMITTED` $\rightarrow$ Ném `InvalidSubmissionStateException`.
- [ ] **UT-BE-06:** `requestHint_pastDeadline_shouldThrowAssignmentExpiredException()`
  - Khi `assignment.getDeadline()` đã qua $\rightarrow$ Ném `AssignmentExpiredException`.
- [ ] **UT-BE-07:** `requestHint_containsJsxGraphJson_shouldSanitizePayloadBeforePrompt()`
  - Truyền `currentContent` chứa `<!-- DRAWINGS_DATA_START ... -->` $\rightarrow$ Verify string gửi sang Prompt Service đã được làm sạch khối JSON thô.
- [ ] **UT-BE-08:** `requestHint_emptyStudentContent_shouldGenerateInitialApproachHint()`
  - Truyền `currentContent = ""` $\rightarrow$ Prompt sinh ra hướng dẫn bắt đầu bài tập thành công.
- [ ] **UT-BE-09:** `requestHint_concurrentRequests_shouldEnforceLockingWithoutDuplicates()`
  - Giả lập 2 threads gọi `requestHint()` đồng thời cho 1 `submissionId` $\rightarrow$ Chỉ 1 thread thành công tạo `hint_number=1`, thread thứ 2 nhận `hint_number=2` hoặc bị lock timeout an toàn.

### 11.2. Backend Integration Tests (`StudentHintControllerTest.java`)

- [ ] **IT-BE-01:** `POST /api/v1/submissions/{assignmentId}/hints` với Token `ROLE_STUDENT` chính chủ $\rightarrow$ Trả về `200 OK`.
- [ ] **IT-BE-02:** `POST /api/v1/submissions/{assignmentId}/hints` với Token Học sinh khác (IDOR Check) $\rightarrow$ Trả về `403 Forbidden`.
- [ ] **IT-BE-03:** `GET /api/v1/submissions/{submissionId}/hints` với Token `ROLE_TEACHER` $\rightarrow$ Trả về danh sách Lịch sử gợi ý thành công `200 OK`.

### 11.3. Frontend Component & Hook Tests (`student-assignment-layout.test.tsx`)

- [ ] **UT-FE-01:** Hiển thị đúng Nút "Cần gợi ý 💡" kèm Badge số lượt (ví dụ `0/3` lượt).
- [ ] **UT-FE-02:** Click nút "Cần gợi ý" $\rightarrow$ Hiển thị state Loading Skeleton và mở Panel/Popover Gợi ý.
- [ ] **UT-FE-03:** Khi `remainingHints === 0` $\rightarrow$ Nút chuyển thành `disabled` và tooltip hiển thị "Đã dùng hết 3/3 lượt gợi ý".
- [ ] **UT-FE-04:** Hiển thị đầy đủ danh sách Lịch sử các lần đã xin gợi ý trước đó với KaTeX toán học được render chuẩn xác.

