# Đặc tả Kỹ thuật Backend (Backend Specification)

**Chức năng:** Admin Dashboard (Quản lý Người dùng & Nhật ký Hệ thống)

---

## 1. Tech Stack & Cấu trúc Thư mục

* **Framework:** Java 21 & Spring Boot 4 (Spring Web, Spring Security, Spring Data JPA).
* **Database:** PostgreSQL 16.
* **Dependencies bảo mật & bổ sung:** `Lombok`, `Spring Security` (để phân quyền dựa trên Role).

### Cấu trúc file dự kiến

* `SecurityConfig.java`: Cấu hình phân quyền bảo mật, yêu cầu role `ADMIN` cho toàn bộ endpoint bắt đầu bằng `/api/admin/**`.
* `AdminUserController.java`: Định nghĩa REST endpoints quản lý người dùng (Giáo viên, Học sinh).
* `AdminLogController.java`: Định nghĩa REST endpoints xem nhật ký hệ thống.
* `AdminUserServiceImpl.java`, `SystemLogServiceImpl.java`: Xử lý nghiệp vụ phân trang, lọc dữ liệu, cập nhật trạng thái user.
* `SystemLog.java`: Entity quản lý nhật ký hệ thống trong database.
* Các DTOs: `UserResponse`, `UserListResponse`, `UpdateUserStatusRequest`, `SystemLogResponse`, `LogFilterRequest`.

---

## 2. Database Schema (Thiết kế thực thể)

Hệ thống bổ sung thêm bảng `system_logs` để lưu trữ chung các hoạt động của người dùng (Audit Log) và lỗi kỹ thuật (Technical Error) để Admin dễ dàng theo dõi.

### Bảng `users` (Chỉ hiển thị các trường liên quan)

* `id`: BIGSERIAL (Primary Key)
* `email`: VARCHAR(255) UNIQUE NOT NULL
* `role`: VARCHAR(20) NOT NULL *(Nhận giá trị: `ADMIN`, `TEACHER`, `STUDENT`)*
* `is_active`: BOOLEAN DEFAULT TRUE *(Dùng để khóa/mở khóa tài khoản)*

### Bảng `system_logs` (Bảng mới)

* `id`: BIGSERIAL (Primary Key)
* `timestamp`: TIMESTAMP NOT NULL *(Thời gian xảy ra sự kiện)*
* `actor`: VARCHAR(100) NOT NULL *(Tên/Email người thực hiện, hoặc chuỗi "SYSTEM" nếu là lỗi hệ thống)*
* `action`: VARCHAR(255) NOT NULL *(Mô tả hành động. Ví dụ: "Xóa tài khoản Học sinh A", "Lỗi kết nối DB")*
* `level`: VARCHAR(20) DEFAULT 'INFO' *(Mức độ sự kiện: `INFO`, `WARNING`, `ERROR`)*
* `user_id`: BIGINT NULLABLE *(Foreign Key liên kết với bảng `users` nếu sự kiện do một user cụ thể thực hiện)*

---

## 3. Quy tắc Nghiệp vụ chi tiết (Business & Security Logic)

### Luồng 1: Phân quyền Truy cập (Authorization)

1. **Kiểm tra JWT Token & Role:** Bất kỳ request nào gửi đến `/api/admin/**` đều phải có JWT token hợp lệ và thuộc tính `role` bên trong token (hoặc từ database) bắt buộc phải là `ADMIN`.
2. **Từ chối truy cập (403 Forbidden):** Nếu `TEACHER` hoặc `STUDENT` cố tình gọi API này, hệ thống phải chặn ngay ở tầng `Security Filter Chain` và trả về mã lỗi HTTP 403.

### Luồng 2: Quản lý Người dùng (Teachers, Students)

1. **Lấy danh sách người dùng (`GET /api/admin/users`):**
    * Bắt buộc hỗ trợ **Phân trang (Pagination)** và **Sắp xếp (Sorting)** để tối ưu hiệu năng.
    * Hỗ trợ tham số truy vấn (Query Params) để lọc theo `role` (TEACHER/STUDENT), `is_active` (trạng thái khóa), và tìm kiếm theo `email`/`name`.
2. **Khóa/Mở khóa tài khoản (`PATCH /api/admin/users/{id}/status`):**
    * Cho phép Admin thay đổi trường `is_active` của một user.
    * Sau khi thực hiện thành công, gọi Service để ghi một dòng log vào bảng `system_logs` với nội dung: `[Admin X] đã [Khóa/Mở khóa] tài khoản [User Y]`.

### Luồng 3: Xem Nhật ký hệ thống (System Logs)

1. **Lấy danh sách Logs (`GET /api/admin/logs`):**
    * Bắt buộc hỗ trợ **Phân trang (Pagination)**. Cần đánh index trên cột `timestamp` trong database để query được nhanh.
    * Hỗ trợ bộ lọc (Filter) theo `level` (INFO, WARNING, ERROR), `actor`, và khoảng thời gian (từ ngày - đến ngày).
2. **Ghi log tự động:**
    * Các service khác trong hệ thống (như `AuthService`, `UserService`) sẽ được inject `SystemLogService` để tự động ghi log vào DB khi có sự kiện quan trọng (ví dụ: đăng nhập sai nhiều lần, lỗi Exception lớn).

---

## 4. Đặc tả API (API Contracts)

### API 1: Lấy danh sách người dùng

* **Endpoint:** `GET /api/admin/users?page=0&size=10&role=TEACHER`
* **Response Thành công (HTTP 200 OK):**

```json
{
  "content": [
    {
      "id": 1,
      "email": "teacher1@example.com",
      "role": "TEACHER",
      "isActive": true
    }
  ],
  "pageNo": 0,
  "pageSize": 10,
  "totalElements": 50,
  "totalPages": 5
}
```

### API 2: Cập nhật trạng thái tài khoản (Khóa/Mở khóa)

* **Endpoint:** `PATCH /api/admin/users/{id}/status`
* **Request Body:**

```json
{
  "isActive": false
}
```

* **Response Thành công (HTTP 200 OK):**

```json
{
  "message": "Trạng thái tài khoản đã được cập nhật thành công."
}
```

### API 3: Lấy danh sách Nhật ký (Logs)

* **Endpoint:** `GET /api/admin/logs?page=0&size=20&level=ERROR`
* **Response Thành công (HTTP 200 OK):**

```json
{
  "content": [
    {
      "id": 102,
      "timestamp": "2026-07-16T10:00:00Z",
      "actor": "SYSTEM",
      "action": "Failed to connect to Mail Server",
      "level": "ERROR"
    }
  ],
  "pageNo": 0,
  "pageSize": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

### API 4: Lấy danh sách các quyền có sẵn & quyền của một Role

* **Endpoint lấy toàn bộ Permission:** `GET /api/admin/roles/permissions`
* **Endpoint lấy Permission của Role (Ví dụ `TEACHER`):** `GET /api/admin/roles/TEACHER/permissions`
* **Response Thành công (HTTP 200 OK):**

```json
[
  {
    "id": 1,
    "name": "DELETE_CLASS",
    "description": "Xóa lớp học"
  },
  {
    "id": 2,
    "name": "CREATE_ASSIGNMENT",
    "description": "Tạo bài tập"
  }
]
```

### API 5: Cập nhật quyền cho Role (Bật/Tắt quyền)

* **Endpoint:** `PUT /api/admin/roles/{role}/permissions` (Ví dụ: `TEACHER`)
* **Request Body:** Truyền lên danh sách `id` của các quyền mà Role đó **được phép** giữ (những ID không có trong mảng sẽ bị thu hồi).

```json
{
  "permissionIds": [1, 2]
}
```

* **Response Thành công (HTTP 200 OK):**

```json
{
  "message": "Cập nhật phân quyền thành công."
}
```

---

## 5. Global Exception Handling (Danh mục mã lỗi trả về)

Hệ thống bắt lỗi tập trung tại `@RestControllerAdvice` và trả về cấu trúc thống nhất cho Frontend:

| Exception | HTTP Status | Cấu trúc dữ liệu Response | Tình huống xảy ra |
| --- | --- | --- | --- |
| `AccessDeniedException` | 403 Forbidden | `{"message": "Bạn không có quyền truy cập tài nguyên này."}` | User không phải là `ADMIN` cố gắng gọi API. |
| `UserNotFoundException` | 404 Not Found | `{"message": "Không tìm thấy người dùng với ID này."}` | Admin truyền ID không tồn tại khi update trạng thái. |
| `MethodArgumentNotValidException` | 400 Bad Request | `{"errors": {"isActive": "Trạng thái không được để trống"}}` | Truyền sai cấu trúc JSON khi cập nhật trạng thái. |
| `InternalServerError` | 500 Internal Server | `{"message": "Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau."}` | Lỗi kết nối Database khi truy vấn log. |
