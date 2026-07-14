# Đặc tả Kỹ thuật Backend (Backend Specification)

**Chức năng:** Phân quyền Động (Dynamic Authorization)

---

## 1. Tech Stack & Cấu trúc Thư mục

* **Framework:** Java 21 & Spring Boot 4 (Spring Web, Spring Security, Spring Data JPA, Spring Cache).
* **Database:** PostgreSQL 16.
* **Caching:** `Caffeine Cache` (In-Memory Local Cache).
* **Dependencies bảo mật & bổ sung:** `Lombok`.

### Cấu trúc file dự kiến

* `SecurityConfig.java`: Cấu hình bật `@EnableCaching` cho Spring Boot.
* `CustomUserDetails.java`: Sửa đổi hàm `getAuthorities()` để nạp thêm các quyền (permissions) vào rổ quyền hạn thay vì chỉ trả về tên Role (như `ROLE_TEACHER`).
* `PermissionCacheService.java`: File xử lý logic lấy dữ liệu phân quyền từ Database và đóng gói vào Cache bằng `@Cacheable`.
* Các Entities mới: `Permission.java`, `RolePermission.java`.
* `DatabaseSeeder.java`: Cập nhật logic để tự động đổ (seed) dữ liệu bảng quyền mẫu vào DB khi khởi chạy dự án lần đầu.

---

## 2. Database Schema (Thiết kế thực thể)

Để chuyển từ phân quyền cứng sang phân quyền động, ta **không thay đổi bảng `users`** (để giữ nguyên enum `Role`), mà bổ sung 2 bảng mới để cấu hình quyền.

### Bảng `permissions`

* `id`: BIGSERIAL (Primary Key)
* `name`: VARCHAR(255) UNIQUE NOT NULL *(Mã quyền truy cập, định dạng `Resource:Action`, ví dụ: `assignment:create`)*
* `description`: VARCHAR(255) NULLABLE *(Mô tả thân thiện, ví dụ: "Quyền tạo bài tập mới")*

### Bảng `role_permissions`

* `id`: BIGSERIAL (Primary Key)
* `role_name`: VARCHAR(50) NOT NULL *(Lưu chuỗi Enum của Role, ví dụ: `TEACHER`, `STUDENT`)*
* `permission_id`: BIGINT NOT NULL *(Foreign Key liên kết với `permissions.id`, ON DELETE CASCADE)*
* **Ràng buộc (Constraint):** Tổ hợp `(role_name, permission_id)` phải là `UNIQUE` để tránh cấp một quyền nhiều lần cho cùng một Role.

---

## 3. Quy tắc Nghiệp vụ chi tiết (Business & Security Logic)

### Luồng 1: Khởi tạo và Nạp cấu hình quyền (Caching)

1. **Truy vấn DB:** Khi một user thực hiện API call và cần kiểm tra quyền, hệ thống (thông qua `PermissionCacheService`) sẽ truy vấn bảng `role_permissions` ghép với `permissions` để lấy danh sách tên các quyền dựa theo tên Role của user đó.
2. **Caffeine Cache:** Kết quả truy vấn sẽ được lưu đệm trực tiếp trên thanh RAM của server (Caffeine Cache) với khóa (key) là tên Role (VD: key=`TEACHER` -> value=`["assignment:create", "submission:grade", ...]`).
3. Lần truy vấn sau của bất kỳ user nào có cùng Role đó sẽ lấy thẳng từ RAM, trả về kết quả gần như tức thời (0 network call, 0 DB query).

### Luồng 2: Xác thực & Đóng gói quyền qua JWT (Authentication)

1. Spring Security bắt request, giải mã JWT token (token hiện tại vẫn giữ nguyên độ nhỏ gọn, chỉ chứa `ID` và `Role`).
2. Lấy `Role` từ token và đưa cho `PermissionCacheService` để lấy danh sách Permissions từ Cache.
3. Khởi tạo đối tượng `CustomUserDetails` bằng cách gộp cả `Role` và toàn bộ danh sách `Permissions` thành một mảng `GrantedAuthority` chuẩn của Spring.

### Luồng 3: Kiểm tra quyền tại API Controller (Authorization)

1. Tại các REST Controller, lập trình viên sử dụng annotation `@PreAuthorize` để giới hạn truy cập.
   * Cú pháp mới: `@PreAuthorize("hasAuthority('tên_quyền')")` thay cho `hasRole()`.
   * Ví dụ: `@PreAuthorize("hasAuthority('assignment:create')")`.
2. Spring Security tự động kiểm tra rổ quyền của user đang gọi API và quyết định cho phép chạy method hoặc ném lỗi ngoại lệ.

### Luồng 4: Cập nhật quyền (Quản trị viên)

1. Admin sử dụng API quản lý để sửa đổi quyền (thêm/xóa quyền của Giáo viên hoặc Học sinh).
2. Code cập nhật dữ liệu bảng `role_permissions` thành công.
3. Lập tức gọi hàm xóa Cache bằng annotation `@CacheEvict(value = "rolePermissions", key = "#roleName")`.
4. Nhờ vậy, ngay trong request kế tiếp, hệ thống phát hiện mất Cache và sẽ bắt buộc tải lại quyền mới nhất từ DB. Đảm bảo cập nhật quyền **Real-time (Thời gian thực)**.

---

## 4. Đặc tả API (Dành cho chức năng Admin)

*(Ghi chú: Nhóm API này dùng để cung cấp giao diện cài đặt phân quyền cho Admin trên Frontend)*

### API 1: Xem quyền của một Role

* **Endpoint:** `GET /api/admin/roles/{roleName}/permissions`
* **Response Thành công (HTTP 200 OK):**

```json
[
  {
    "id": 1,
    "name": "assignment:create",
    "description": "Tạo bài tập mới"
  },
  {
    "id": 2,
    "name": "submission:grade",
    "description": "Chấm điểm bài nộp"
  }
]
```

### API 2: Cập nhật lại quyền cho Role

* **Endpoint:** `PUT /api/admin/roles/{roleName}/permissions`
* **Request Body:** *(Danh sách các ID của quyền muốn gán)*

```json
{
  "permissionIds": [1, 2, 5, 8]
}
```

* **Logic:** Xóa toàn bộ quyền cũ của Role này trong bảng `role_permissions`, insert danh sách mới, và trigger `@CacheEvict`.

---

## 5. Global Exception Handling (Danh mục mã lỗi trả về)

Hệ thống xử lý ngoại lệ chung tại `@RestControllerAdvice`:

| Exception | HTTP Status | Cấu trúc dữ liệu Response | Tình huống xảy ra |
| --- | --- | --- | --- |
| `AccessDeniedException` | 403 Forbidden | `{"message": "Bạn không có quyền thực hiện thao tác này."}` | Người dùng cố truy cập API nhưng không có Authority phù hợp trong cấu hình. |
| `DataIntegrityViolationException` | 400 Bad Request | `{"message": "Lỗi dữ liệu: Quyền này không tồn tại hoặc đã được gán."}` | Lỗi xảy ra khi Admin cố gắng gán một `permission_id` không tồn tại trong DB vào Role. |
