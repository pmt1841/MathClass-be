# Spec: Hệ thống Dynamic Tag Bài tập (Dynamic Assignment Tag System)

## 1. Objective (Mục tiêu)

Tính năng này nhằm thiết kế lại hệ thống Gắn nhãn / Phân loại bài tập cho vai trò **Giáo viên** trong hệ thống `MathClass-service`.
* Chuyển đổi từ cơ chế phân loại 3 nhóm cố định (*Khối lớp, Phân môn, Độ khó*) sang **Hệ thống Dynamic Tag linh hoạt** (cho phép nhập chuỗi tag tự do như `#Lớp_10`, `#Bất_đẳng_thức`, `#Kiểm_tra_15p`).
* **Tự động lưu & Tra cứu gợi ý (Autocomplete Search)**: Khi giáo viên gõ ký tự vào ô nhập tag (ví dụ: `L`, `10`), hệ thống tra cứu và xổ gợi ý danh sách các tag đã tồn tại từ CSDL. Các tag mới được nhập lần đầu sẽ tự động lưu vào CSDL cho các lần gợi ý sau.
* **Quy tắc gắn tag linh hoạt (Phương án B)**: Tag **không bắt buộc** khi tạo bài nháp thủ công, sinh bài AI hoặc bóc tách file DOCX/PDF. Tuy nhiên, bài tập **bắt buộc phải có tối thiểu 1 tag** khi gạt công khai lên Thư viện cộng đồng (`PUBLIC`).
* **Cho phép sửa tag bài PUBLIC**: Giáo viên có thể trực tiếp thêm/xóa tag của bài tập ngay cả khi bài tập đang ở trạng thái `PUBLIC`. Tag mới được đồng bộ tức thì sang Thư viện cộng đồng.

---

## 2. Tech Stack & Environment

* **Language & Framework**: Java 21, Spring Boot 4.1.0, Spring Data JPA.
* **Database**: PostgreSQL 16 (chạy trong Docker container `mathclass-db`).
* **Security**: Spring Security (Phân quyền theo **Permission/Authority** thông qua `@PreAuthorize("hasAuthority(...)")`).

---

## 3. Build & Test Commands

```bash
# Biên dịch ứng dụng
./gradlew build -x test

# Chạy Unit & Integration Tests
./gradlew test

# Chạy ứng dụng local
./gradlew bootRun

# Khởi chạy môi trường Docker
docker-compose up --build -d
```

---

## 4. Project Structure (Cấu trúc thư mục liên quan)

```
src/main/java/com/codegym/mathclass/
├── assignment/
│   ├── controller/
│   │   ├── TagController.java                   # [MODIFY] REST API /api/v1/tags bổ sung query search
│   │   └── AssignmentController.java            # [MODIFY] REST API /api/v1/assignments
│   ├── dto/
│   │   ├── TagResponse.java                     # Trả về thông tin tag (id, name, active)
│   │   ├── CreateAssignmentRequest.java         # [MODIFY] Thêm List<String> tagNames
│   │   └── UpdateAssignmentRequest.java         # [MODIFY] Thêm List<String> tagNames
│   ├── entity/
│   │   ├── Tag.java                             # [MODIFY] Chuyển type thành nullable / GENERAL
│   │   ├── TagType.java                         # Enum TagType
│   │   └── AssignmentTag.java                   # Entity liên kết (assignment_id, tag_id)
│   ├── repository/
│   │   ├── TagRepository.java                   # [MODIFY] Thêm query findByActiveTrueAndNameContainingIgnoreCase
│   │   └── AssignmentTagRepository.java         # Quản lý liên kết assignment_tags
│   └── service/
│       ├── TagService.java                      # [MODIFY] Bổ sung replaceTagsByName, searchTags, requireCompletePublicTags
│       └── impl/
│           ├── TagServiceImpl.java              # [MODIFY] Xử lý logic tự động lưu tag mới & check PUBLIC (>= 1 tag)
│           └── AssignmentServiceImpl.java       # [MODIFY] Tích hợp replaceTagsByName
```

---

## 5. Detailed Specifications & Schemas

### 5.1 Data Model & Entity Specifications

#### A. Bảng `tags` (Cập nhật `Tag.java`)
* `id`: `BIGINT`, PK, Auto-increment.
* `name`: `VARCHAR(100)`, NOT NULL, UNIQUE (Chuỗi tên tag đã được trim khoảng trắng).
* `type`: `VARCHAR(50)`, NULLABLE (Tùy chọn, mặc định `null` hoặc `GENERAL`).
* `active`: `BOOLEAN`, NOT NULL, Default = `true`.
* `created_at`, `updated_at`: Thuộc tính audit từ `BaseEntity`.

#### B. Bảng `assignment_tags` (Cập nhật `AssignmentTag.java`)
* `assignment_id`: `BIGINT`, FK trỏ tới `assignments(id)`.
* `tag_id`: `BIGINT`, FK trỏ tới `tags(id)`.
* Ràng buộc UNIQUE (`assignment_id`, `tag_id`).

---

### 5.2 API Specifications & Authorization

#### A. Tra cứu & Gợi ý Autocomplete Tag
* **Endpoint**: `GET /api/v1/tags`
* **Phân quyền**: Người dùng đã đăng nhập (`Authenticated`).
* **Query Parameters**:
  * `query` (String, optional): Từ khóa tìm kiếm tag (ví dụ: `lớp`, `10`).
* **Logic xử lý**:
  * Nếu `query` có giá trị ➔ Gọi `tagRepository.findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(query)`.
  * Nếu `query` trống ➔ Trả về toàn bộ tag đang `active = true` sắp xếp theo tên.
* **Response**: `List<TagResponse>` (`id`, `name`, `active`).

#### B. Tạo mới Bài tập (kèm Tag linh hoạt)
* **Endpoint**: `POST /api/v1/assignments`
* **Phân quyền**: `@PreAuthorize("hasAuthority('assignment:create')")`
* **Request Payload**:
```json
{
  "title": "Bài tập Ôn tập Hàm số",
  "content": "...",
  "visibility": "PRIVATE",
  "tagNames": ["Lớp 10", "Đại số", "Đồ thị"]
}
```
* **Logic xử lý**:
  1. Tạo `Assignment` với trạng thái `visibility` tương ứng.
  2. Nếu `visibility == PUBLIC`, gọi `tagService.requireCompletePublicTags(assignment)` ➔ Kiểm tra `tagNames` phải có ít nhất 1 tag không rỗng. Ném `BadRequestException` nếu thiếu tag.
  3. Gọi `tagService.replaceTagsByName(assignment, request.getTagNames())`:
     * Loại bỏ trùng lặp và trim khoảng trắng từng tên tag.
     * Tìm tag trong DB theo tên (case-insensitive): Nếu chưa có ➔ Tự động lưu mới `Tag(name, active=true)`.
     * Tạo liên kết trong bảng `assignment_tags`.

#### C. Cập nhật Bài tập & Thẻ bài tập (kèm Tag linh hoạt)
* **Endpoint**: `PUT /api/v1/assignments/{id}`
* **Phân quyền**: `@PreAuthorize("hasAuthority('assignment:update')")`
* **Logic xử lý**:
  1. Cập nhật thông tin bài tập.
  2. Nếu bài tập đang ở trạng thái `PUBLIC` hoặc cập nhật sang `PUBLIC`, kiểm tra bài tập phải có **tối thiểu 1 tag**.
  3. Cho phép cập nhật danh sách tag kể cả khi bài tập đang `PUBLIC`. Khi cập nhật thành công, dữ liệu tag được đồng bộ ngay lên Thư viện cộng đồng.

---

## 6. Boundaries (Ranh giới & Quy tắc)

* **Luôn làm (`Always do`)**:
  * Chuẩn hóa tên tag (loại bỏ khoảng trắng thừa 2 đầu) trước khi lưu vào CSDL.
  * So sánh tên tag không phân biệt hoa thường (case-insensitive) để tránh tạo tag trùng lặp (`Lớp 10` = `lớp 10`).
  * Cho phép bài nháp/private lưu mà không cần tag.
  * Kiểm tra ít nhất 1 tag khi bài tập ở trạng thái `PUBLIC`.
  * Dùng `BadRequestException` kèm thông báo tiếng Việt rõ ràng khi vi phạm rule.
* **Cần xác nhận trước (`Ask first`)**:
  * Thay đổi cấu trúc bảng `assignment_tags`.
* **Không bao giờ làm (`Never do`)**:
  * Chặn giáo viên chỉnh sửa tag khi bài tập đang ở trạng thái `PUBLIC`.
  * Xóa dữ liệu tag gốc trong CSDL khi một bài tập gỡ bỏ tag đó.
  * Trả về HTTP 500 khi người dùng nhập tag trùng hoặc gửi mảng tag rỗng.

---

## 7. Success Criteria (Tiêu chí Nghiệm thu)

1. [ ] API `GET /api/v1/tags?query={q}` lọc và trả về danh sách gợi ý tag chính xác theo từ khóa không phân biệt hoa thường.
2. [ ] Gửi `tagNames` mới trong `POST /api/v1/assignments` hoặc `PUT /api/v1/assignments/{id}` tự động tạo mới `Tag` vào CSDL nếu chưa có.
3. [ ] Bài tập trạng thái `PRIVATE` lưu thành công ngay cả khi mảng `tagNames` rỗng hoặc `null`.
4. [ ] Bài tập chuyển sang `PUBLIC` bị từ chối với lỗi `400 Bad Request` nếu chưa có tag nào được đính kèm.
5. [ ] Bài tập đang `PUBLIC` cập nhật tag mới thành công và hiển thị tag mới trên Thư viện cộng đồng.
6. [ ] JUnit tests và API Integration tests bao phủ toàn bộ luồng tạo, cập nhật, autocomplete và public validation.

---

## 8. Comprehensive Test Cases Matrix (Danh sách Test Cases kiểm thử)

| Mã TC | Phân loại | Tên Test Case | Điều kiện đầu vào / Bước thực hiện | Kết quả mong đợi (Expected Outcome) |
| :--- | :--- | :--- | :--- | :--- |
| **TC01** | **Autocomplete** | Tìm kiếm tag theo từ khóa khớp 1 phần | Gọi `GET /api/v1/tags?query=lớp`. | Trả về danh sách các tag chứa từ "lớp" (ví dụ: `Lớp 10`, `Lớp 11`). |
| **TC02** | **Autocomplete** | Tìm kiếm tag với từ khóa viết hoa/thường | Gọi `GET /api/v1/tags?query=ĐẠI+SỐ`. | Trả về tag `Đại số` (không phân biệt hoa thường). |
| **TC03** | **Autocomplete** | Tìm kiếm tag không có kết quả | Gọi `GET /api/v1/tags?query=xyz123`. | Trả về mảng rỗng `[]` (HTTP 200 OK). |
| **TC04** | **Create Assignment** | Tạo bài nháp `PRIVATE` không đính kèm tag | Gửi `POST /api/v1/assignments` với `visibility = PRIVATE` và `tagNames = []`. | Tạo bài tập thành công (HTTP 201 Created), không bị chặn validation. |
| **TC05** | **Create Assignment** | Tạo bài nháp kèm tag hoàn toàn mới | Gửi `POST /api/v1/assignments` với `tagNames = ["Bất đẳng thức Cosi"]`. | Bài tập được lưu, tag `Bất đẳng thức Cosi` tự động tạo mới trong bảng `tags`. |
| **TC06** | **Create Assignment** | Tạo bài `PUBLIC` không có tag | Gửi `POST /api/v1/assignments` với `visibility = PUBLIC` và `tagNames = []`. | Trả về lỗi `400 Bad Request`: "Bài tập cần có ít nhất 1 tag trước khi công khai lên Thư viện cộng đồng." |
| **TC07** | **Create Assignment** | Tạo bài `PUBLIC` có 1 tag trở lên | Gửi `POST /api/v1/assignments` với `visibility = PUBLIC` và `tagNames = ["Lớp 10"]`. | Tạo bài tập công khai thành công (HTTP 201 Created). |
| **TC08** | **Update Tag** | Cập nhật tag cho bài tập `PRIVATE` | Gọi `PUT /api/v1/assignments/{id}` gửi `tagNames = ["Hình học", "Lớp 11"]`. | Bài tập được cập nhật lại danh sách tag tương ứng. |
| **TC09** | **Update Tag (Public)** | Cập nhật tag trực tiếp cho bài tập đang `PUBLIC` | Gọi `PUT /api/v1/assignments/{id}` của bài `PUBLIC` với `tagNames = ["Đại số 12"]`. | Cập nhật tag thành công (HTTP 200 OK). Bài tập trong Thư viện hiển thị tag `Đại số 12`. |
| **TC10** | **Update Tag (Public)** | Gỡ bỏ toàn bộ tag của bài tập đang `PUBLIC` | Gọi `PUT /api/v1/assignments/{id}` của bài `PUBLIC` với `tagNames = []`. | Trả về lỗi `400 Bad Request` do vi phạm quy tắc tối thiểu 1 tag khi công khai. |
| **TC11** | **Normalization** | Nhập tag có khoảng trắng thừa và ký tự hoa thường | Gửi `tagNames = ["  Lớp 10  ", "đại số"]`. | CSDL lưu tag chuẩn hóa dạng `Lớp 10` và `đại số`, không bị trùng lặp. |
| **TC12** | **Authorization** | Đăng nhập tài khoản Học sinh gọi API lấy danh sách tag | Gọi `GET /api/v1/tags` bằng token vai trò `STUDENT`. | Phản hồi `200 OK` và trả về danh sách tag active. |
