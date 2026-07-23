# Specification: System Audit Log Backend (`MathClass-service`)

## 1. Executive Summary & Objectives

Hệ thống Nhật ký Kiểm vết Backend (System Audit Log) trong dịch vụ **MathClass-service** chịu trách nhiệm tự động ghi nhận, lưu trữ và cung cấp API truy vấn các thao tác quản lý dữ liệu (CRUD) từ phía Admin/Giáo viên và các sự cố lỗi hệ thống.

---

## 2. Architecture & Design Specifications

### 2.1. Entity Model (`SystemLog`)
- **File:** `com.codegym.mathclass.systemlog.entity.SystemLog`
- **Inheritance:** Extends `com.codegym.mathclass.common.entity.BaseEntity` (Kế thừa `id`, `createdAt`, `updatedAt`).
- **Database Table:** `system_logs`

#### Schema Attributes:
| Attribute | Field Name | Data Type | Constraints / Details |
| :--- | :--- | :--- | :--- |
| Primary Key | `id` | `long` | Auto-increment ID (từ `BaseEntity`) |
| Timestamp | `createdAt` | `LocalDateTime` | mốc thời gian diễn ra (từ `BaseEntity`) |
| Actor | `actor` | `String` | Email/Username người thao tác |
| Action | `action` | `String` | Mô tả ngắn gọn hành động |
| Log Level | `level` | `SystemLogLevel` | Enum: `INFO`, `WARNING`, `ERROR` |
| Resource Type | `resourceType` | `String` | Danh mục: `USER`, `ROLE`, `COMMUNITY_REPO`, `SYSTEM` |
| Resource ID | `resourceId` | `String` | ID đối tượng bị tác động |
| Client IP | `ipAddress` | `String` | IPv4 / IPv6 của người dùng |
| User Agent | `userAgent` | `String` | Trình duyệt & Thiết bị |
| Status | `status` | `String` | Trạng thái: `SUCCESS`, `FAILED` |

---

### 2.2. Spring AOP & Annotation Driven Audit Logging
- **Annotation:** `com.codegym.mathclass.systemlog.annotation.AuditLog`
  - Thuộc tính: `action()`, `resourceType()` (default `"SYSTEM"`), `level()` (default `INFO`).
- **Aspect:** `com.codegym.mathclass.systemlog.aspect.AuditLogAspect`
  - Đánh chặn các phương thức có `@AuditLog`.
  - Tự động lấy `actor` từ `SecurityContextHolder`.
  - Tự động trích xuất `ipAddress` và `userAgent` từ `RequestContextHolder` / `HttpServletRequest`.
  - Bắt kết quả thực thi và ghi log `SUCCESS` hoặc `FAILED` nếu có Exception.

---

### 2.3. Repository & Dynamic Specification Query
- **File:** `com.codegym.mathclass.systemlog.repository.SystemLogRepository`
- Extends `JpaRepository<SystemLog, Long>` và `JpaSpecificationExecutor<SystemLog>`.
- Khắc phục triệt để lỗi tham số `NULL` trên PostgreSQL thông qua `Specification<SystemLog>` động.

---

### 2.4. Scheduled Log Retention
- **File:** `com.codegym.mathclass.systemlog.scheduler.LogRetentionScheduler`
- Tự động xóa các log cũ quá 90 ngày (`app.audit-log.retention-days=90`).
- Chạy lúc **2:00 AM** hàng ngày bằng `@Scheduled(cron = "0 0 2 * * ?")`.

---

### 2.5. REST API Endpoint Specification

#### API: Get Audit Logs
- **Endpoint:** `GET /api/admin/logs`
- **Security:** `@PreAuthorize("hasRole('ADMIN')")`
- **Query Parameters:**
  - `level` (Optional): `INFO`, `WARNING`, `ERROR`
  - `resourceType` (Optional): `USER`, `ROLE`, `COMMUNITY_REPO`, `SYSTEM`
  - `actor` (Optional): Email người dùng (search chứa chuỗi, case-insensitive)
  - `startDate` (Optional): ISO Date Time (`2026-07-22T00:00:00`)
  - `endDate` (Optional): ISO Date Time (`2026-07-22T23:59:59`)
  - `page` (Default: 0), `size` (Default: 20), `sort` (Default: `createdAt,desc`)
- **Response Format:** `ResponseEntity<Page<SystemLogResponse>>`

---

## 3. Verification & Testing Strategy
1. **Compilation Check:** `./gradlew compileJava`
2. **Aspect Test:** Gắn `@AuditLog` lên method Admin và kiểm tra dữ liệu được tạo tự động trong bảng `system_logs`.
3. **API Test:** Kiểm tra endpoint `GET /api/admin/logs` với các tham số bộ lọc đa chiều trên Swagger/Postman.
