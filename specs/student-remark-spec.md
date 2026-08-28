# Specification: Quản lý Lịch sử Nhận xét Học sinh (Student Remarks - Điểm mạnh & Điểm yếu)

## 1. Executive Summary & Objectives

Tính năng **Nhận xét học sinh (Student Remarks)** cho phép giáo viên phụ trách lớp học ghi nhận và theo dõi sự tiến bộ của từng học sinh trong lớp theo thời gian thông qua các đánh giá đa chiều:
- **Điểm mạnh & Ưu điểm (Strengths):** Ghi nhận sở trường, năng khiếu, sự tiến bộ (ví dụ: tư duy logic tốt, phản xạ nhanh,...).
- **Điểm yếu & Cần cải thiện (Weaknesses):** Chỉ ra các lỗ hổng kiến thức hoặc thói quen cần khắc phục (ví dụ: hay tính toán ẩu, quên đặt điều kiện nghiệm,...).
- **Đánh giá chung & Lời khuyên (General Assessment):** Nhận xét định hướng, lời khuyên phương pháp học tập.

### Mục tiêu chính:
1. **Lưu vết theo dòng thời gian (Historical Tracking):** Mỗi nhận xét được lưu thành một bản ghi riêng biệt với mốc thời gian thực (`createdAt`) và thông tin giáo viên đánh giá.
2. **Bảo mật & Phân quyền chặt chẽ:**
   - Chỉ giáo viên phụ trách lớp mới có quyền tạo / xóa nhận xét (`classroom:manage_requests`).
   - Giáo viên phụ trách và chính học sinh đó mới có quyền xem lịch sử nhận xét của học sinh. Các học sinh khác trong lớp không được xem nhận xét của bạn học.
3. **Hiển thị linh hoạt trên giao diện:** Hỗ trợ xem dạng danh sách thu gọn (Dropdown/Accordion), mở rộng chi tiết và thao tác tức thời.

---

## 2. Acceptance Criteria Checklist (AC)

### 2.1. Phân quyền & Bảo mật (Security & Authorization)
- [ ] **AC-01:** Chỉ người dùng có quyền `classroom:manage_requests` (Giáo viên chủ nhiệm lớp) mới được phép gọi API tạo và xóa nhận xét học sinh.
- [ ] **AC-02:** Khi truy vấn danh sách nhận xét (`GET /api/v1/classrooms/{classCode}/students/{studentId}/remarks`), chỉ giáo viên phụ trách lớp hoặc chính học sinh sở hữu `studentId` mới được phép truy cập. Người dùng khác sẽ nhận lỗi `403 Forbidden` (`AccessDeniedException`).
- [ ] **AC-03:** Giáo viên chỉ có thể nhận xét những học sinh thực sự là thành viên của lớp (`classroom.students`). Nếu `studentId` không thuộc lớp, trả về lỗi `400 Bad Request`.

### 2.2. Nghiệp vụ Nhận xét (Business Logic)
- [ ] **AC-04:** Khi tạo nhận xét mới, yêu cầu phải có ít nhất 1 trong 3 trường (`strengths`, `weaknesses`, `generalAssessment`) có nội dung không rỗng. Nếu cả 3 đều rỗng/null, trả về `400 Bad Request`.
- [ ] **AC-05:** Tự động cắt bỏ khoảng trắng thừa đầu/cuối chuỗi (`trim()`) trước khi lưu vào Database.
- [ ] **AC-06:** Danh sách nhận xét trả về luôn được sắp xếp theo thời gian tạo mới nhất lên đầu (`ORDER BY createdAt DESC`).
- [ ] **AC-07:** Khi xóa nhận xét (`DELETE /api/v1/classrooms/{classCode}/students/{studentId}/remarks/{remarkId}`), hệ thống xác thực nhận xét có tồn tại và thuộc quyền quản lý của giáo viên lớp học, trả về HTTP status `204 No Content`.

---

## 3. Database Schema & Entity Design

### Bảng `student_remarks`

```sql
CREATE TABLE student_remarks (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strengths TEXT,
    weaknesses TEXT,
    general_assessment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_student_remarks_lookup ON student_remarks(classroom_id, student_id, created_at DESC);
```

### JPA Entity `StudentRemark`
- Kế thừa từ `BaseEntity` (chứa `id`, `createdAt`, `updatedAt`).
- `@ManyToOne(fetch = FetchType.LAZY) classroom`
- `@ManyToOne(fetch = FetchType.LAZY) student`
- `@ManyToOne(fetch = FetchType.LAZY) teacher`
- `@Column(name = "strengths", columnDefinition = "TEXT") private String strengths;`
- `@Column(name = "weaknesses", columnDefinition = "TEXT") private String weaknesses;`
- `@Column(name = "general_assessment", columnDefinition = "TEXT") private String generalAssessment;`

---

## 4. API Endpoints Specification

### 4.1. Lấy danh sách lịch sử nhận xét
- **Method / URL:** `GET /api/v1/classrooms/{classCode}/students/{studentId}/remarks`
- **Headers:** `Authorization: Bearer <JWT_TOKEN>`
- **Response 200 OK:**
```json
[
  {
    "id": 1,
    "studentId": 2,
    "studentName": "Học sinh Lê Thị Bình",
    "teacherId": 1,
    "teacherName": "Thầy Nguyễn Văn A",
    "teacherAvatarUrl": null,
    "strengths": "Tư duy logic tốt, phản xạ nhanh",
    "weaknesses": "Hay nhầm lẫn dấu khi tính toán",
    "generalAssessment": "Cần luyện tập thêm các bài toán rút gọn",
    "createdAt": "2026-08-27T10:45:00Z",
    "updatedAt": "2026-08-27T10:45:00Z"
  }
]
```

### 4.2. Tạo nhận xét mới
- **Method / URL:** `POST /api/v1/classrooms/{classCode}/students/{studentId}/remarks`
- **Security:** `@PreAuthorize("hasAuthority('classroom:manage_requests')")`
- **Request Body:**
```json
{
  "strengths": "Học sinh hiểu bài nhanh, chăm chỉ phát biểu",
  "weaknesses": "Trình bày bài giải còn vắn tắt",
  "generalAssessment": "Khuyến khích rèn luyện thêm tính cẩn thận khi làm bài tự luận"
}
```
- **Response 201 Created:** Trả về đối tượng `StudentRemarkResponse`.

### 4.3. Xóa nhận xét
- **Method / URL:** `DELETE /api/v1/classrooms/{classCode}/students/{studentId}/remarks/{remarkId}`
- **Security:** `@PreAuthorize("hasAuthority('classroom:manage_requests')")`
- **Response 204 No Content**

### 4.4. AI Quét Dữ liệu & Đánh giá Tiến độ Học sinh
- **Method / URL:** `POST /api/v1/classrooms/{classCode}/students/{studentId}/remarks/ai-evaluate`
- **Security:** `@PreAuthorize("hasAuthority('classroom:manage_requests')")`
- **Task Code:** `STUDENT_REMARK` (System Prompt: `PROMPT_STUDENT_REMARK`)
- **Credit Policy:** Phí tối thiểu 5 credit / 1000 tokens = 1 credit (`AiCreditConfig`).
- **Request Body:**
```json
{
  "days": 7,
  "startDate": "2026-08-21",
  "endDate": "2026-08-28"
}
```
- **Response 200 OK:**
```json
{
  "startDate": "2026-08-21",
  "endDate": "2026-08-28",
  "totalAssignments": 5,
  "completedAssignments": 4,
  "overdueAssignments": 0,
  "activeIncompleteAssignments": 1,
  "averageScore": 8.75,
  "strengths": "Nắm chắc kiến thức giải phương trình, phản xạ nhanh và trình bày lời giải rõ ràng.",
  "weaknesses": "Còn hay nhầm lẫn dấu khi biến đổi biểu thức chứa phân thức.",
  "generalAssessment": "Trong khoảng thời gian từ 21/08/2026 đến 28/08/2026, học sinh đã hoàn thành 4/5 bài tập được giao (1 bài còn lại vẫn trong hạn nộp). Cần rèn luyện thêm tính cẩn thận và chủ động hoàn thành bài tập còn lại trước thời hạn."
}
```

---

## 5. Decision Log

| STT | Vấn đề | Quyết định chọn | Lý do |
| :--- | :--- | :--- | :--- |
| 1 | Lưu nhận xét dạng snapshot ghi đè hay lịch sử nhiều mốc thời gian | Lưu lịch sử nhiều bản ghi theo thời gian (`List<StudentRemark>`) | Giúp giáo viên và phụ huynh/học sinh theo dõi được toàn bộ tiến trình thay đổi, tiến bộ qua từng tuần/tháng. |
| 2 | Phân tách 3 trường: Điểm mạnh, Điểm yếu, Đánh giá chung | Tách thành 3 cột riêng biệt thay vì 1 trường comment duy nhất | Cấu trúc dữ liệu rõ ràng, dễ phân tích, hiển thị trực quan dạng badge màu (Xanh lá / Vàng cam / Xanh dương). |
| 3 | Quyền truy cập API xem nhận xét | Giáo viên phụ trách lớp và chính học sinh được nhận xét | Đảm bảo tính bảo mật và sự riêng tư cá nhân của học sinh. |
| 4 | Cấu hình AI Đánh giá học sinh | Quản lý qua Task `STUDENT_REMARK`, seed prompt `PROMPT_STUDENT_REMARK` và cấu hình qua Admin | Không hardcode prompt hay logic tính phí trong code, hỗ trợ dynamic credit & provider routing. |

---

## 6. Verification Strategy & Test Plan

### 6.1. Backend Test Matrix & Coverage
| Layer | Test Suite Class | Test Case | Target / Scenarios | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Controller** | `StudentRemarkControllerTest` | `getStudentRemarks_ReturnsList` | GET `/remarks` trả về 200 OK + danh sách nhận xét | ✅ Pass |
| **Controller** | `StudentRemarkControllerTest` | `createStudentRemark_ReturnsCreated` | POST `/remarks` trả về 201 Created + DTO | ✅ Pass |
| **Controller** | `StudentRemarkControllerTest` | `deleteStudentRemark_ReturnsNoContent` | DELETE `/remarks/{id}` trả về 204 No Content | ✅ Pass |
| **Controller** | `StudentRemarkControllerTest` | `evaluateStudentWithAi_ReturnsEvaluationResponse` | POST `/ai-evaluate` trả về 200 OK + AI evaluation DTO | ✅ Pass |
| **Service** | `StudentRemarkServiceImplTest` | `getStudentRemarks_TeacherAccess_Success` | Giáo viên truy vấn nhận xét học sinh trong lớp | ✅ Pass |
| **Service** | `StudentRemarkServiceImplTest` | `getStudentRemarks_StudentSelfAccess_Success` | Học sinh tự xem nhận xét của chính mình | ✅ Pass |
| **Service** | `StudentRemarkServiceImplTest` | `getStudentRemarks_OtherStudentAccess_Forbidden` | Học sinh khác xem bị ném `AccessDeniedException` | ✅ Pass |
| **Service** | `StudentRemarkServiceImplTest` | `createStudentRemark_Success` | Tạo nhận xét thành công và lưu database | ✅ Pass |
| **Service** | `StudentRemarkServiceImplTest` | `createStudentRemark_AllFieldsBlank_ThrowsBadRequest` | Cả 3 trường rỗng ném `BadRequestException` | ✅ Pass |
| **Service** | `StudentRemarkServiceImplTest` | `deleteStudentRemark_Success` | Xóa nhận xét thành công | ✅ Pass |
| **AI Service** | `StudentRemarkAiServiceImplTest` | `evaluateStudentProgress_Success_ValidTimeframeAndData` | Quét bài tập, tính điểm TB, parse JSON nhận xét từ AI | ✅ Pass |
| **AI Service** | `StudentRemarkAiServiceImplTest` | `evaluateStudentProgress_Deadlines_ClassifiesOverdueAndActive` | Phân loại bài tập quá hạn vs bài tập còn hạn làm | ✅ Pass |
| **AI Service** | `StudentRemarkAiServiceImplTest` | `evaluateStudentProgress_InvalidDateRange_ThrowsBadRequestException` | `startDate` sau `endDate` ném `BadRequestException` | ✅ Pass |
| **AI Service** | `StudentRemarkAiServiceImplTest` | `evaluateStudentProgress_NotTeacher_ThrowsAccessDeniedException` | Không phải giáo viên lớp ném `AccessDeniedException` | ✅ Pass |
| **AI Service** | `StudentRemarkAiServiceImplTest` | `evaluateStudentProgress_StudentNotMember_ThrowsBadRequestException` | Học sinh ngoài lớp ném `BadRequestException` | ✅ Pass |
| **AI Service** | `StudentRemarkAiServiceImplTest` | `evaluateStudentProgress_AiReturnsPlainText_GracefulFallback` | Phản hồi AI dạng plain text tự động fallback an toàn | ✅ Pass |

### 6.2. Commands Verification
```bash
# 1. Kiểm tra biên dịch Java
./gradlew compileJava

# 2. Chạy toàn bộ Test suite của Classroom (Controller + Service + AI Service)
./gradlew test --tests "com.codegym.mathclass.classroom.*"
```

**Kết quả kiểm tra:** `BUILD SUCCESSFUL` (100% tests passing).
