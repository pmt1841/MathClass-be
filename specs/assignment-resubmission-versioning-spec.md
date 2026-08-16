# Specification: Assignment Resubmission & Version History (`MathClass-service` & `MathClass-fe`)

---

## 1. Feature Overview
- **Feature Name:** Cho phép học sinh chỉnh sửa và nộp lại bài làm sau khi nhận phản hồi của giáo viên (Assignment Resubmission & Version History)
- **Jira Ticket:** [MAT-194](https://phanvanluan611996.atlassian.net/browse/MAT-194)
- **Target Subsystems:** `MathClass-service` (Backend Spring Boot), `MathClass-fe` (Next.js App Router)
- **Target Users:** 
  - **Student (Học sinh):** Xem nhận xét/điểm số của giáo viên, sửa sai và nộp lại bài làm mới.
  - **Teacher (Giáo viên):** Bật/tắt tính năng cho phép nộp lại khi giao bài; xem lịch sử các lần nộp (đối chiếu bài cũ và bài mới), chấm lại bài.

---

## 2. Business Goals & Core Objectives
1. **Khuyến khích học sinh sửa sai và cải thiện:** Giúp học sinh học tập dựa trên phản hồi (feedback-driven learning), được phép sửa bài và nộp lại nếu giáo viên cho phép.
2. **Lưu trữ lịch sử minh bạch (Version History Snapshot):** Toàn bộ các lần nộp bài trước đó (nội dung, hình vẽ, điểm số, lời nhận xét của giáo viên, thời gian nộp) được lưu trữ nguyên vẹn để cả giáo viên và học sinh đều có thể đối chiếu.
3. **Giữ nguyên vẹn kiến trúc và luồng dữ liệu chính:** 
   - Bảng `submissions` vẫn là bảng chính (**Single Source of Truth** cho trạng thái hiện tại). Mọi API hiện tại của hệ thống (danh sách nộp, làm bài, chấm điểm, gợi ý AI, vẽ hình...) không bị xáo trộn logic.
   - Bảng `submission_versions` chỉ đóng vai trò là kho lưu trữ Snapshot (bản sao lưu vết) khi học sinh bấm nộp lại.

---

## 3. Potential Logic Loopholes & Mitigations (Edge Cases)

### 3.1. Case 1: Học sinh cố tình nộp lại khi bài tập đã quá hạn nộp (Past Deadline)
- **Vấn đề:** Bài tập đã hết hạn deadline, nhưng học sinh vẫn còn nút nộp lại hoặc cố tình gửi request `POST /resubmit`.
- **Khắc phục:** Backend bắt buộc kiểm tra `assignment.getDeadline()`. Nếu `LocalDateTime.now().isAfter(deadline)`, lập tức trả về `400 Bad Request` ("Đã hết hạn nộp bài tập, không thể nộp lại").

### 3.2. Case 2: Giáo viên tắt tính năng "Cho phép nộp lại" sau khi học sinh đã nộp lần đầu
- **Vấn đề:** Lúc đầu giáo viên bật `allowResubmit = true`, nhưng sau đó giáo viên cập nhật bài tập tắt `allowResubmit = false`.
- **Khắc phục:** Mọi request `POST /resubmit` đều kiểm tra `assignment.isAllowResubmit()` thời gian thực. Nếu `false`, trả về `400 Bad Request` ("Bài tập này không cho phép nộp lại"). Frontend ẩn nút "Chỉnh sửa & Nộp lại".

### 3.3. Case 3: Race Condition khi học sinh bấm nộp lại liên tiếp (Double Click / Concurrent Requests)
- **Vấn đề:** Học sinh bấm nút "Nộp lại" 2 lần thật nhanh tạo 2 request song song, có thể dẫn đến việc tạo 2 bản ghi snapshot trùng lặp hoặc nhảy sai số phiên bản (`version_number`).
- **Khắc phục:** 
  - Backend sử dụng `@Transactional` và tìm phiên bản lớn nhất `max(versionNumber)` của submission để tăng `version_number = max + 1`.
  - Frontend disable nút và hiển thị hiệu ứng loading ngay khi gửi request.

### 3.4. Case 4: Bảo toàn Lời nhận xét và Điểm số cũ khi nộp lại
- **Vấn đề:** Khi học sinh nộp lại, nếu xóa điểm cũ và nhận xét cũ ở bảng `submissions` trước khi kịp lưu vào `submission_versions`, dữ liệu chấm bài trước đó của giáo viên sẽ bị mất vĩnh viễn.
- **Khắc phục:** Đảm bảo thứ tự thực hiện nghiêm ngặt:
  1. Đọc dữ liệu hiện tại của `Submission` (gồm `content`, `score`, `teacherFeedback`, `submittedAt`).
  2. Tạo bản ghi `SubmissionVersion` lưu trữ trọn vẹn trạng thái cũ.
  3. Cập nhật `Submission` với `content` mới, `status = SUBMITTED`, `submittedAt = now()`, `score = null`, `teacherFeedback = null`.

### 3.5. Case 5: Xử lý Trạng thái bài nộp khi đang sửa dở (Draft sau khi bấm Resubmit)
- **Vấn đề:** Học sinh bấm "Sửa & Nộp lại", hệ thống chuyển sang chế độ soạn thảo. Nếu học sinh lưu nháp (`DRAFT`), bài nộp không được tính là đã nộp cho đến khi học sinh bấm nút "Xác nhận nộp bài".
- **Khắc phục:** 
  - Khi học sinh bấm "Sửa bài", bài nộp chuyển sang trạng thái nháp để chỉnh sửa.
  - Chỉ khi học sinh bấm **"Nộp lại"** (hoặc lưu với `status = SUBMITTED`), hệ thống mới đóng gói phiên bản mới và gửi thông báo cho giáo viên.

---

## 4. Functional Requirements (FR)

- **FR-1 (Teacher Configuration):** Giáo viên có thể Bật/Tắt checkbox `allowResubmit` khi tạo bài tập mới hoặc chỉnh sửa bài tập hiện có.
- **FR-2 (Student Resubmission Trigger):** Khi bài tập có `allowResubmit = true`, học sinh đã có bài nộp ở trạng thái `GRADED` hoặc `SUBMITTED` có thể bấm nút "Chỉnh sửa & Nộp lại" (miễn là chưa hết hạn nộp).
- **FR-3 (Version Snapshot Recording):** Mỗi lần học sinh nộp bài mới/nộp lại, hệ thống tự động lưu bản snapshot của lần nộp trước vào bảng `submission_versions`.
- **FR-4 (Teacher Version History View):** Trên giao diện chấm bài của giáo viên (`SubmissionDetail`), hiển thị bộ chọn phiên bản (Version Selector / Tabs: `Lần 1`, `Lần 2 (Mới nhất)`).
  - Khi chọn phiên bản cũ: hiển thị bài làm cũ, điểm cũ và lời nhận xét cũ ở chế độ chỉ đọc.
  - Khi chọn phiên bản mới nhất: hiển thị bài làm mới và form chấm điểm/gửi nhận xét mới.
- **FR-5 (Student Version History View):** Học sinh có thể xem lại lịch sử các phiên bản đã nộp và lời nhận xét của giáo viên qua từng lần nộp.
- **FR-6 (Notification):** Khi học sinh nộp lại bài, hệ thống gửi email và notification thông báo cho giáo viên biết học sinh đã nộp lại phiên bản mới.

---

## 5. Data Model & Database Design

### 5.1. Entity `Assignment` (`assignments`)
Thêm trường mới:
```sql
ALTER TABLE assignments ADD COLUMN allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE;
```
- JPA Field:
  ```java
  @Builder.Default
  @Column(name = "allow_resubmit", nullable = false)
  private boolean allowResubmit = false;
  ```

### 5.2. Entity `SubmissionVersion` (`submission_versions`)
Bảng mới lưu trữ Snapshot các phiên bản nộp bài:
```sql
CREATE TABLE submission_versions (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    content TEXT NOT NULL,
    score DOUBLE PRECISION,
    teacher_feedback TEXT,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submission_version UNIQUE (submission_id, version_number)
);

CREATE INDEX idx_submission_versions_sub_id ON submission_versions(submission_id);
```

JPA Entity class:
```java
@Entity
@Table(name = "submission_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"submission_id", "version_number"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "score")
    private Double score;

    @Column(name = "teacher_feedback", columnDefinition = "TEXT")
    private String teacherFeedback;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
```

---

## 6. API Contracts & Specifications

### 6.1. Cập nhật API Bài tập (Assignment APIs)
- `POST /api/v1/assignments` & `PUT /api/v1/assignments/{id}`:
  - Request Body bổ sung: `"allowResubmit": true/false`
- `GET /api/v1/assignments/{id}`:
  - Response DTO bổ sung: `"allowResubmit": true/false`

### 6.2. API Nộp lại bài (Resubmit API)
- **Endpoint:** `POST /api/v1/submissions/{submissionId}/resubmit`
- **Headers:** `Authorization: Bearer <jwt_token>`
- **Request Body (`SubmissionRequest`):**
  ```json
  {
    "content": "Nội dung bài làm sửa đổi của học sinh...",
    "status": "SUBMITTED"
  }
  ```
- **Response `200 OK` (`ApiResponse<SubmissionResponse>`):**
  ```json
  {
    "success": true,
    "message": "Nộp lại bài tập thành công",
    "data": {
      "id": 12,
      "assignmentId": 5,
      "studentId": 101,
      "studentName": "Nguyễn Văn A",
      "content": "Nội dung bài làm sửa đổi...",
      "status": "SUBMITTED",
      "score": null,
      "teacherFeedback": null,
      "submittedAt": "2026-08-14T10:35:00",
      "versionNumber": 2
    }
  }
  ```

### 6.3. API Lấy Lịch sử Phiên bản (Version History API)
- **Endpoint:** `GET /api/v1/submissions/{submissionId}/versions`
- **Headers:** `Authorization: Bearer <jwt_token>`
- **Quyền hạn:** Giáo viên phụ trách bài tập HOẶC Học sinh sở hữu bài nộp.
- **Response `200 OK` (`ApiResponse<List<SubmissionVersionResponse>>`):**
  ```json
  {
    "success": true,
    "message": "Lấy lịch sử phiên bản thành công",
    "data": [
      {
        "id": 1,
        "versionNumber": 1,
        "content": "Nội dung bài làm lần 1...",
        "score": 6.5,
        "teacherFeedback": "Cần xem lại câu 2 và sửa lại phương trình.",
        "submittedAt": "2026-08-10T14:20:00"
      },
      {
        "id": 2,
        "versionNumber": 2,
        "content": "Nội dung bài làm lần 2 đã sửa...",
        "score": null,
        "teacherFeedback": null,
        "submittedAt": "2026-08-14T10:35:00"
      }
    ]
  }
  ```

---

## 7. Frontend UI / UX Specifications

### 7.1. Cấu hình bài tập cho Giáo viên (`AssignmentForm.tsx`)
- Trong sidebar **Cấu hình bài tập**, thêm một toggle Switch hoặc Checkbox:
  - Label: **Cho phép nộp lại sau khi chấm**
  - Subtext: *Học sinh có thể chỉnh sửa và nộp lại bài làm để cải thiện điểm sau khi nhận nhận xét của giáo viên.*

### 7.2. Giao diện làm bài của Học sinh (`student-assignment-layout.tsx`)
- Khi bài làm đã ở trạng thái `GRADED` (hoặc `SUBMITTED`) và `assignment.allowResubmit === true`:
  - Hiển thị nút **"Chỉnh sửa & Nộp lại"** (bên cạnh hiển thị điểm số hiện tại).
  - Khi học sinh bấm vào, mở khóa editor cho phép chỉnh sửa bài làm.
  - Khi hoàn thành, bấm nút **"Xác nhận nộp lại"** kèm hộp thoại xác nhận ("Bài làm của bạn sẽ được nộp lại và lưu phiên bản cũ để giáo viên đối chiếu").
  - Hiển thị menu/tab **"Lịch sử nộp bài"** để xem lại các lần nộp và điểm/nhận xét trước đây.

### 7.3. Giao diện chấm bài của Giáo viên (`submission-detail.tsx`)
- Phía trên bài làm học sinh, nếu bài có từ 2 phiên bản trở lên, hiển thị thanh chuyển đổi phiên bản (**Version Switcher**):
  - `[ Lần nộp 1 (10/08 - 6.5đ) ]` `[ Lần nộp 2 (14/08 - Mới nhất) ]`
- Khi chọn phiên bản cũ: Bài làm hiển thị dạng Read-only kèm điểm và nhận xét cũ.
- Khi chọn phiên bản mới nhất: Hiển thị bài làm mới và form chấm điểm sẵn sàng cho giáo viên nhập điểm mới.

---

## 8. Verification & Acceptance Criteria (AC Checklist)

- [ ] **AC-1:** Giáo viên có thể bật/tắt `allowResubmit` khi tạo hoặc sửa bài tập.
- [ ] **AC-2:** Khi `allowResubmit = false`, học sinh sau khi nộp/chấm không thể nộp lại (nút nộp lại bị ẩn, API chặn `400 Bad Request`).
- [ ] **AC-3:** Khi `allowResubmit = true` và chưa quá hạn: Học sinh có thể sửa bài và bấm "Nộp lại".
- [ ] **AC-4:** Khi nộp lại, phiên bản cũ được lưu vào `submission_versions` kèm nội dung, điểm và nhận xét cũ.
- [ ] **AC-5:** Giáo viên xem chi tiết bài nộp có thể duyệt xem qua lại giữa các phiên bản để so sánh sự tiến bộ.
- [ ] **AC-6:** Giáo viên chấm điểm cho phiên bản mới thành công và lưu điểm số mới vào hệ thống.
- [ ] **AC-7:** Kiểm tra toàn bộ Unit Test và Build không lỗi (`./gradlew test`, `npx tsc --noEmit`).
