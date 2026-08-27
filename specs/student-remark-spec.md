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

---

## 5. Decision Log

| STT | Vấn đề | Quyết định chọn | Lý do |
| :--- | :--- | :--- | :--- |
| 1 | Lưu nhận xét dạng snapshot ghi đè hay lịch sử nhiều mốc thời gian | Lưu lịch sử nhiều bản ghi theo thời gian (`List<StudentRemark>`) | Giúp giáo viên và phụ huynh/học sinh theo dõi được toàn bộ tiến trình thay đổi, tiến bộ qua từng tuần/tháng. |
| 2 | Phân tách 3 trường: Điểm mạnh, Điểm yếu, Đánh giá chung | Tách thành 3 cột riêng biệt thay vì 1 trường comment duy nhất | Cấu trúc dữ liệu rõ ràng, dễ phân tích, hiển thị trực quan dạng badge màu (Xanh lá / Vàng cam / Xanh dương). |
| 3 | Quyền truy cập API xem nhận xét | Giáo viên phụ trách lớp và chính học sinh được nhận xét | Đảm bảo tính bảo mật và sự riêng tư cá nhân của học sinh. |

---

## 6. Verification Strategy & Test Plan

### 6.1. Unit Test Suites
1. **`StudentRemarkServiceImplTest`**:
   - `getStudentRemarks`:
     - Test thành công khi người gọi là giáo viên của lớp.
     - Test thành công khi người gọi chính là học sinh sở hữu nhận xét.
     - Test ném `AccessDeniedException` khi người gọi là học sinh khác trong lớp.
     - Test ném `ResourceNotFoundException` khi lớp học không tồn tại.
   - `createStudentRemark`:
     - Test thành công khi giáo viên lớp tạo nhận xét hợp lệ.
     - Test ném `BadRequestException` khi học sinh không thuộc lớp học.
     - Test ném `BadRequestException` khi tất cả các trường nhận xét đều rỗng.
     - Test ném `AccessDeniedException` khi người gọi không phải giáo viên lớp.
   - `deleteStudentRemark`:
     - Test xóa thành công nhận xét.
     - Test ném `ResourceNotFoundException` khi không tìm thấy nhận xét.
     - Test ném `AccessDeniedException` khi người dùng không có quyền xóa.
2. **`StudentRemarkControllerTest`**:
   - Test ánh xạ URL, status code (200, 201, 204), và serialization DTO.

### 6.2. Commands Verification
```bash
# Chạy biên dịch Backend
./gradlew compileJava

# Chạy toàn bộ Test suite của Classroom
./gradlew test --tests "com.codegym.mathclass.classroom.*"
```
