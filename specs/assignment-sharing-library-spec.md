# Spec: Tính năng Chia sẻ Bài tập & Thư viện Bài tập dùng chung (Assignment Sharing & Shared Resource Library)

## 1. Objective (Mục tiêu)
Tính năng này nhằm nâng cao khả năng tái sử dụng tài nguyên học liệu giữa các Giáo viên trong hệ thống `MathClass-service`.
* Giáo viên có thể thiết lập trạng thái chia sẻ **`PRIVATE` (Riêng tư)** hoặc **`PUBLIC` (Công khai)** cho cả **Bài tập đơn lẻ (`Assignment`)** và **Phiếu bài tập (`AssignmentSheet`)**.
* Các bài tập/phiếu ở trạng thái `PUBLIC` sẽ xuất hiện trên **Thư viện / Ngân hàng đề dùng chung** (`/api/library/...`), cho phép tất cả người dùng có quyền tìm kiếm, xem và **Clone (nhân bản)** về kho bài tập cá nhân.
* **Ghi nhận nguồn gốc (`originalAuthor`)**: Khi Giáo viên B clone bài tập từ Giáo viên A, bài tập clone sẽ ghi nhận thông tin `originalAuthor = Giáo viên A` để tri ân và phục vụ việc truy xuất nguồn gốc.
* **Độc lập dữ liệu**: Nếu Giáo viên A chuyển trạng thái bài tập từ `PUBLIC` về lại `PRIVATE`, bài tập sẽ ẩn khỏi Thư viện. Tuy nhiên, các bản clone đã tạo trong tài khoản Giáo viên B vẫn được giữ nguyên và hoạt động độc lập.

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
│   │   ├── AssignmentController.java              # RequestMapping("/api/assignments")
│   │   ├── AssignmentSheetController.java         # RequestMapping("/api/assignment-sheets")
│   │   └── AssignmentLibraryController.java       # [NEW] REST API Thư viện @RequestMapping("/api/library")
│   ├── dto/
│   │   ├── AssignmentResponse.java                # [MODIFY] Thêm visibility, originalAuthor
│   │   ├── AssignmentSheetResponse.java           # [MODIFY] Thêm visibility, originalAuthor
│   │   ├── CreateAssignmentRequest.java           # [MODIFY] Thêm visibility
│   │   ├── UpdateAssignmentRequest.java           # [MODIFY] Thêm visibility
│   │   ├── CreateAssignmentSheetRequest.java      # [MODIFY] Thêm visibility
│   │   └── UpdateAssignmentSheetRequest.java      # [MODIFY] Thêm visibility
│   ├── entity/
│   │   ├── AssignmentVisibility.java              # [NEW] Enum PRIVATE, PUBLIC
│   │   ├── Assignment.java                        # [MODIFY] Thêm visibility, originalAuthor
│   │   └── AssignmentSheet.java                   # [MODIFY] Thêm visibility, originalAuthor
│   ├── repository/
│   │   ├── AssignmentRepository.java              # [MODIFY] Thêm phương thức truy vấn Library
│   │   └── AssignmentSheetRepository.java         # [MODIFY] Thêm phương thức truy vấn Library
│   └── service/
│       ├── AssignmentService.java
│       ├── AssignmentSheetService.java
│       └── impl/
│           ├── AssignmentServiceImpl.java         # [MODIFY] Logic clone & library
│           └── AssignmentSheetServiceImpl.java    # [MODIFY] Logic clone & library
├── config/
│   └── DatabaseSeeder.java                        # [MODIFY] Seed các Permission mới: library:read, library:clone
└── security/config/
    └── SecurityConfig.java                        # [MODIFY] Cấu hình bảo mật
```

---

## 5. Detailed Specifications & Schemas

### 5.1 Data Model & Permissions

#### A. Enum `AssignmentVisibility`
```java
public enum AssignmentVisibility {
    PRIVATE,  // Chỉ người tạo thấy và sử dụng (Mặc định)
    PUBLIC    // Công khai trên Thư viện dùng chung
}
```

#### B. Thêm Permission mới trong Hệ thống (`permissions`)
Hệ thống quản lý phân quyền theo **Permission** (Authority String). Các Permission mới được thêm vào DB qua `DatabaseSeeder`:
* `library:read` - Quyền xem và tìm kiếm bài tập/phiếu bài tập công khai trong Thư viện.
* `library:clone` - Quyền clone bài tập/phiếu bài tập từ Thư viện về kho cá nhân.

*Phân bổ Permission mặc định trong Seeder*:
* Role `TEACHER`: Được gán `library:read`, `library:clone` (cùng các quyền `assignment:*`).
* Role `ADMIN`: Được gán đầy đủ quyền quản trị.

#### C. Bảng `assignments` (Cập nhật `Assignment.java`)
* `visibility`: `VARCHAR(20)`, NOT NULL, Default = `'PRIVATE'`.
* `original_author_id`: `BIGINT`, NULL, Foreign Key trỏ tới `users(id)`.

#### D. Bảng `assignment_sheets` (Cập nhật `AssignmentSheet.java`)
* `visibility`: `VARCHAR(20)`, NOT NULL, Default = `'PRIVATE'`.
* `original_author_id`: `BIGINT`, NULL, Foreign Key trỏ tới `users(id)`.

---

### 5.2 API Specifications & Authorization

#### A. Quản lý Quyền chia sẻ bài tập cá nhân
* **Tạo / Cập nhật Bài tập**: Kiểm tra `@PreAuthorize("hasAuthority('assignment:create')")` và `@PreAuthorize("hasAuthority('assignment:update')")`.
* Các request DTO (`CreateAssignmentRequest`, `UpdateAssignmentRequest`, `CreateAssignmentSheetRequest`, `UpdateAssignmentSheetRequest`) nhận thêm trường `visibility` (Default: `PRIVATE`).

#### B. API Thư viện Bài tập (`/api/library`)

##### 1. Tìm kiếm Bài tập đơn lẻ công khai
* **Endpoint**: `GET /api/library/assignments`
* **Phân quyền**: `@PreAuthorize("hasAuthority('library:read')")`
* **Query Parameters**:
  * `keyword` (String, optional): Tìm kiếm theo tiêu đề.
  * `page` (int, default 0), `size` (int, default 10).
* **Điều kiện lọc trong Query**:
  * `visibility = PUBLIC`
  * `classroom IS NULL`
  * `status != DELETED`
* **Response**: `Page<AssignmentResponse>` (bao gồm thông tin `originalAuthor`).

##### 2. Clone Bài tập đơn lẻ từ Thư viện
* **Endpoint**: `POST /api/library/assignments/{id}/clone`
* **Phân quyền**: `@PreAuthorize("hasAuthority('library:clone')")`
* **Logic xử lý**:
  1. Kiểm tra bài tập tồn tại và có `visibility == PUBLIC`.
  2. Tạo bản sao mới của `Assignment`:
     * `teacher` = Người dùng đang đăng nhập (`User` hiện tại).
     * `originalAuthor` = Tác giả gốc (`originalAuthor` của bài gốc nếu có, hoặc chính tác giả bài gốc).
     * `visibility` = `PRIVATE`.
     * `status` = `DRAFT`.
     * `classroom` = `null`, `deadline` = `null`.
     * Copy toàn bộ `title`, `description`, `content`, danh sách `drawings` và `images`.
  3. Trả về `AssignmentResponse` của bài tập vừa clone.

##### 3. Tìm kiếm Phiếu bài tập công khai
* **Endpoint**: `GET /api/library/assignment-sheets`
* **Phân quyền**: `@PreAuthorize("hasAuthority('library:read')")`
* **Query Parameters**: `keyword`, `page`, `size`.
* **Điều kiện lọc**:
  * `visibility = PUBLIC`
  * `classroom IS NULL`
* **Response**: `Page<AssignmentSheetResponse>`.

##### 4. Clone Phiếu bài tập từ Thư viện
* **Endpoint**: `POST /api/library/assignment-sheets/{id}/clone`
* **Phân quyền**: `@PreAuthorize("hasAuthority('library:clone')")`
* **Logic xử lý**:
  1. Kiểm tra phiếu tồn tại và có `visibility == PUBLIC`.
  2. Tạo bản sao `AssignmentSheet` mới cho người dùng với `visibility = PRIVATE`, `originalAuthor = Tác giả gốc`.
  3. Clone tất cả các bài tập con (`AssignmentSheetItem`) trong phiếu sang bản sao mới.

---

## 6. Boundaries (Ranh giới & Quy tắc)

* **Luôn làm (`Always do`)**:
  * Sử dụng đúng prefix chuẩn `/api/...` (không dùng `/api/v1/...`).
  * Kiểm tra quyền thông qua `@PreAuthorize("hasAuthority(...)")` ở cấp Controller/Method.
  * Đặt `visibility = PRIVATE` và `status = DRAFT` cho các bản clone mới tạo từ Thư viện.
  * Giữ nguyên `originalAuthor` kể cả khi người clone chỉnh sửa tiêu đề hay nội dung của bài clone.
  * Seed 2 Permission mới (`library:read`, `library:clone`) trong `DatabaseSeeder.java`.
* **Cần xác nhận trước (`Ask first`)**:
  * Thay đổi các Permission đã tồn tại trong DB.
* **Không bao giờ làm (`Never do`)**:
  * Thêm prefix `/v1` vào các endpoint API.
  * Dùng `hasRole(...)` cứng để kiểm tra quyền hạn; luôn sử dụng Permission (`hasAuthority(...)`).
  * Xóa hoặc hủy bỏ bài tập của giáo viên khác khi tác giả gốc thay đổi trạng thái từ `PUBLIC` về `PRIVATE`.

---

## 7. Success Criteria (Tiêu chí Nghiệm thu)

1. [ ] Đã sửa đường dẫn các API theo đúng chuẩn `/api/library/...` (không chứa `/v1`).
2. [ ] `DatabaseSeeder` tự động khởi tạo 2 Permission `library:read` và `library:clone` và gán cho các Role thích hợp.
3. [ ] Phân quyền bảo mật kiểm tra chính xác bằng Authority (`@PreAuthorize("hasAuthority('library:read')")` và `@PreAuthorize("hasAuthority('library:clone')")`).
4. [ ] CSDL tự động bổ sung 2 cột `visibility` và `original_author_id` trong 2 bảng `assignments` và `assignment_sheets`.
5. [ ] Người dùng có quyền có thể tùy chọn `PRIVATE` hoặc `PUBLIC` khi tạo/chỉnh sửa Bài tập hoặc Phiếu bài tập.
6. [ ] API `GET /api/library/assignments` và `GET /api/library/assignment-sheets` trả về các bài tập công khai (`PUBLIC`) chính xác.
7. [ ] API Clone nhân bản thành công Bài tập/Phiếu bài tập cho người dùng, hiển thị đúng thông tin tác giả gốc (`originalAuthor`).
8. [ ] Khi tác giả đổi bài gốc từ `PUBLIC` -> `PRIVATE`, bài biến mất khỏi Thư viện nhưng bản clone trong tài khoản khác vẫn tồn tại và hoạt động độc lập.

---

## 8. Comprehensive Test Cases Matrix (Danh sách Test Cases kiểm thử)

| Mã TC | Phân loại | Tên Test Case | Điều kiện đầu vào / Bước thực hiện | Kết quả mong đợi (Expected Outcome) |
| :--- | :--- | :--- | :--- | :--- |
| **TC01** | **CRUD & Visibility** | Tạo bài tập đơn lẻ ở trạng thái `PUBLIC` | Gửi `POST /api/assignments` với `visibility = "PUBLIC"`. | Bài tập được khởi tạo thành công với `visibility = PUBLIC`, `status = DRAFT`. |
| **TC02** | **CRUD & Visibility** | Tạo bài tập đơn lẻ không truyền `visibility` | Gửi `POST /api/assignments` không kèm trường `visibility`. | Hệ thống tự động gán giá trị mặc định `visibility = PRIVATE`. |
| **TC03** | **CRUD & Visibility** | Chuyển bài tập từ `PRIVATE` sang `PUBLIC` | Giáo viên A gọi `PUT /api/assignments/{id}` gửi `visibility = "PUBLIC"`. | `visibility` cập nhật thành `PUBLIC`. Bài tập xuất hiện khi tìm kiếm trong Thư viện. |
| **TC04** | **CRUD & Visibility** | Chuyển bài tập từ `PUBLIC` về `PRIVATE` | Giáo viên A gọi `PUT /api/assignments/{id}` gửi `visibility = "PRIVATE"`. | `visibility` cập nhật thành `PRIVATE`. Bài tập lập tức biến mất khỏi Thư viện. |
| **TC05** | **CRUD & Visibility** | Tạo Phiếu bài tập ở trạng thái `PUBLIC` | Gửi `POST /api/assignment-sheets` với `visibility = "PUBLIC"`. | Phiếu bài tập được lưu thành công với `visibility = PUBLIC`. |
| **TC06** | **Search & Discovery** | Tìm kiếm Bài tập lẻ trong Thư viện | Gọi `GET /api/library/assignments` với tài khoản có `library:read`. | Trả về danh sách phân trang các bài tập có `visibility = PUBLIC` và `classroom IS NULL`. |
| **TC07** | **Search & Discovery** | Bài tập `PRIVATE` không xuất hiện trong Thư viện | Tạo bài tập với `visibility = PRIVATE`, sau đó gọi `GET /api/library/assignments`. | Bài tập `PRIVATE` **không** nằm trong kết quả trả về của Thư viện. |
| **TC08** | **Search & Discovery** | Tìm kiếm bài tập Thư viện theo từ khóa `keyword` | Gọi `GET /api/library/assignments?keyword=Đại+số`. | Trả về các bài tập công khai có tiêu đề chứa chuỗi "Đại số". |
| **TC09** | **Search & Discovery** | Tìm kiếm Phiếu bài tập công khai | Gọi `GET /api/library/assignment-sheets`. | Trả về các Phiếu bài tập có `visibility = PUBLIC` và `classroom IS NULL`. |
| **TC10** | **Cloning & Ownership** | Clone Bài tập đơn lẻ từ Thư viện | Giáo viên B gọi `POST /api/library/assignments/{id}/clone` đối với 1 bài tập `PUBLIC` của Giáo viên A. | Tạo bản clone mới: `teacher = Giáo viên B`, `originalAuthor = Giáo viên A`, `visibility = PRIVATE`, `status = DRAFT`. Sao chép đủ content, drawings, images. |
| **TC11** | **Cloning & Ownership** | Clone bài tập KHÔNG công khai (`PRIVATE`) | Giáo viên B cố gắng gọi clone 1 bài tập có `visibility = PRIVATE`. | Hệ thống từ chối và trả về lỗi `400 Bad Request` hoặc `404 Not Found` (không tìm thấy bài công khai). |
| **TC12** | **Cloning & Ownership** | Clone Phiếu bài tập từ Thư viện | Giáo viên B gọi `POST /api/library/assignment-sheets/{id}/clone`. | Clone thành công Phiếu bài tập và toàn bộ các Bài tập con trong phiếu cho Giáo viên B, lưu vết `originalAuthor`. |
| **TC13** | **Cloning & Ownership** | Kiểm tra tính độc lập của bản clone khi bài gốc bị ẩn | 1. Giáo viên A tạo bài `PUBLIC`.<br>2. Giáo viên B clone bài đó.<br>3. Giáo viên A đổi bài gốc về `PRIVATE`. | Bài gốc ẩn khỏi Thư viện. Bản clone trong tài khoản Giáo viên B **vẫn tồn tại nguyên vẹn** và sử dụng bình thường. |
| **TC14** | **Authorization** | Đăng nhập tài khoản có quyền `library:read` | Gọi `GET /api/library/assignments` bằng User có Permission `library:read`. | Phản hồi `200 OK` và trả về dữ liệu danh sách Thư viện. |
| **TC15** | **Authorization** | Từ chối truy cập Thư viện khi thiếu Permission | Gọi `GET /api/library/assignments` bằng User (ví dụ Học sinh) KHÔNG có `library:read`. | Phản hồi `403 Forbidden`. |
| **TC16** | **Authorization** | Từ chối Clone khi thiếu Permission `library:clone` | Gọi `POST /api/library/assignments/{id}/clone` bằng User KHÔNG có `library:clone`. | Phản hồi `403 Forbidden`. |
