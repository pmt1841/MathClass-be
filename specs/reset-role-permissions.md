# Spec: Chức năng Khôi phục Phân quyền Mặc định cho Vai trò (Reset Role Permissions to Default)

## Objective
Hệ thống cung cấp giải pháp cho Quản trị viên (Admin) khôi phục cài đặt danh sách quyền (Permissions) của từng vai trò (`TEACHER`, `STUDENT`) về cấu hình chuẩn ban đầu của hệ thống.

Khi thực hiện khôi phục:
1. Xóa toàn bộ liên kết quyền tùy chỉnh hiện tại của vai trò được chọn trong bảng `RolePermission`.
2. Tạo lại các bản ghi `RolePermission` tương ứng với danh sách quyền mặc định.
3. Hủy bỏ (evict) toàn bộ Cache phân quyền đang lưu trữ.
4. Ghi nhận Nhật ký Hệ thống (System Log) với cấp độ Warning để phục vụ kiểm vết.

---

## Tech Stack
* **Java 21** & **Spring Boot 4.x** (Spring Web, Spring Security, Spring Data JPA).
* **Database:** PostgreSQL 16 (bảng `permission` và `role_permission`).
* **Caching & Auditing:** Spring Cache (Permission Cache Service) & `SystemLogService`.

---

## Project Structure
Các tệp nguồn liên quan trực tiếp đến tính năng này:
* [DefaultRolePermissions.java](../src/main/java/com/codegym/mathclass/user/config/DefaultRolePermissions.java) -> Class hằng số quy định danh sách quyền mặc định cho từng vai trò (`TEACHER`, `STUDENT`, `ADMIN`).
* [DatabaseSeeder.java](../src/main/java/com/codegym/mathclass/config/DatabaseSeeder.java) -> Tái sử dụng hằng số `DefaultRolePermissions` khi khởi tạo dữ liệu DB lần đầu.
* [RolePermissionService.java](../src/main/java/com/codegym/mathclass/user/service/RolePermissionService.java) -> Thêm phương thức `@Transactional public void resetRolePermissionsToDefault(Role role)`.
* [AdminPermissionController.java](../src/main/java/com/codegym/mathclass/user/controller/AdminPermissionController.java) -> Thêm REST endpoint `POST /api/admin/roles/{roleName}/reset-permissions`.

---

## Boundaries
* **Always:**
  * Yêu cầu quyền truy cập `@PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")`.
  * Xóa cache phân quyền ngay lập tức sau khi cập nhật DB thành công bằng `permissionCacheService.evictAllPermissionsCache()`.
  * Ghi Log hệ thống bằng `systemLogService.logWarning(...)`.
* **Never:**
  * Không cho phép reset role không tồn tại (ném ra `BadRequestException`).
  * Không cho phép trả về phản hồi 200 OK nếu DB update thất bại.

---

## Success Criteria & API Specification

### Endpoint
`POST /api/admin/roles/{roleName}/reset-permissions`

### Path Variables
* `roleName`: Tên vai trò (Ví dụ: `TEACHER`, `STUDENT`, `teacher`, `student`).

### Headers
* `Authorization: Bearer <JWT_TOKEN>`

### Responses

#### 200 OK (Thành công)
```json
{
  "message": "Khôi phục phân quyền mặc định thành công."
}
```

#### 400 Bad Request (Role không hợp lệ)
```json
{
  "message": "Role không hợp lệ: INVALID_ROLE"
}
```

#### 403 Forbidden (Không đủ quyền)
```json
{
  "message": "Access Denied"
}
```
