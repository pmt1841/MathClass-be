# Đặc Tả Kỹ Thuật (Specification): Tính Năng Báo Cáo Lỗi Hệ Thống (System Bug Reporting)

## 1. Tổng Quan & Mục Tiêu

Tính năng **Báo cáo lỗi hệ thống** cho phép người dùng (gồm người dùng chưa đăng nhập tại trang Login và người dùng đã đăng nhập với vai trò Học sinh, Giáo viên) gửi thông tin phản hồi sự cố cho Quản trị viên (Admin). Admin có thể theo dõi, xem chi tiết và cập nhật trạng thái xử lý sự cố tại trang quản lý riêng.

---

## 2. Yêu Cầu Chức Năng (Functional Requirements)

### 2.1. Phân Hệ Người Dùng (Client / End-user)
* **Khách chưa đăng nhập (Trang Login):**
  * Hiển thị nút "Báo cáo lỗi" / "Báo cáo".
  * Mở Modal Popup Form Báo cáo lỗi.
  * Ô nhập **Email liên hệ** là bắt buộc (`required`).
* **Người dùng đã đăng nhập (Giáo viên & Học sinh):**
  * Thêm mục **"Báo cáo sự cố"** ở Sidebar điều hướng (Mở Modal Popup Form Báo cáo lỗi).
  * Tự động điền (Auto-fill) Email và Tên hiển thị từ tài khoản hiện tại, đồng thời **khóa ô nhập Email (read-only)**.
  * **Đổi tên** mục menu hiện tại của Giáo viên từ *"Báo cáo & Thống kê"* ➔ **"Báo cáo sử dụng"** (giữ nguyên route `/reports`).
* **Quy định trong Form Báo cáo:**
  * **Loại lỗi (Bắt buộc):** Danh sách chọn gồm 5 nhóm:
    1. Lỗi đăng nhập / tài khoản (`LOGIN_ACCOUNT`)
    2. Lỗi hiển thị giao diện / công thức Toán KaTeX (`UI_KATEX`)
    3. Lỗi không nộp bài / không tải được đề bài (`SUBMISSION_PROBLEM`)
    4. Lỗi tốc độ / không phản hồi (`PERFORMANCE`)
    5. Lỗi khác (`OTHER`)
  * **Mô tả sự cố:** Không bắt buộc (Optional).
  * **File ảnh đính kèm:** Cho phép đính kèm tối đa **3 ảnh**, dung lượng tối đa **5MB / ảnh**. Tái sử dụng UI & logic uploader ảnh bài tập hiện tại.

### 2.2. Phân Hệ Quản Trị (Admin)
* **Sidebar Admin:** Thêm mục **"Quản lý Báo cáo lỗi"** ➔ Dẫn đến route `/admin/bug-reports`.
* **Trang danh sách (`/admin/bug-reports`):**
  * Bảng hiển thị cột **STT (1, 2, 3...)** được tính theo công thức `(Trang - 1) * Số dòng + Chỉ số + 1`. **Tuyệt đối không hiển thị cột ID DB / UUID**.
  * Cột thông tin: STT, Người gửi (Email & Tên), Loại lỗi, Thời gian gửi (**Múi giờ Việt Nam UTC+7 / ICT** dạng `DD/MM/YYYY HH:mm:ss`), Trạng thái (`PENDING`, `IN_PROGRESS`, `RESOLVED`), Cột Thao tác ("Chi tiết").
* **Modal Xem Chi Tiết:**
  * Hiển thị đầy đủ thông tin báo cáo, mô tả sự cố.
  * Xem danh sách ảnh đính kèm (cho phép click phóng to lightbox).
  * Cho phép Admin cập nhật Trạng thái xử lý (`PENDING` - Chờ xử lý, `IN_PROGRESS` - Đang xử lý, `RESOLVED` - Đã giải quyết).

---

## 3. Nhật Ký Quyết Định (Decision Log)

| STT | Vấn đề | Quyết định chọn | Lý do lựa chọn & Phương án thay thế |
|---|---|---|---|
| 1 | Danh mục loại lỗi | 5 nhóm lỗi mặc định | Phân loại rõ ràng giúp Admin dễ phân nhóm sự cố. |
| 2 | Thông tin người gửi khi đã login | Auto-fill & Read-only | Đảm bảo tính chính xác thông tin tài khoản gửi báo cáo. |
| 3 | UX Form báo cáo | Modal Popup | Không ngắt luồng công việc hiện tại của người dùng. |
| 4 | Trạng thái báo cáo | `PENDING`, `IN_PROGRESS`, `RESOLVED` | Quản lý tiến độ xử lý từ tiếp nhận đến hoàn thành. |
| 5 | Thời gian hiển thị | UTC+7 / ICT (`Asia/Ho_Chi_Minh`) | Chuẩn múi giờ Việt Nam. |
| 6 | Đặt tên API Endpoint | Sử dụng `@ApiVersion(1)` không ghi cứng `/v1` | Tuân thủ chuẩn REST Controller của `MathClass-service`. |

---

## 4. Đặc Tả Backend (`MathClass-service`)

### 4.1. Entity Schema (JPA)

Package: `com.codegym.mathclass.bugreport.entity`

#### Entity `BugReport` (Bảng `bug_reports`)
* `id`: PK (`BaseEntity`)
* `reporter_email`: `VARCHAR(255)` NOT NULL
* `reporter_name`: `VARCHAR(255)` NULLABLE
* `user_id`: `Long` NULLABLE (FK to `users.id`)
* `error_type`: `EnumType.STRING` NOT NULL (`LOGIN_ACCOUNT`, `UI_KATEX`, `SUBMISSION_PROBLEM`, `PERFORMANCE`, `OTHER`)
* `description`: `TEXT` NULLABLE
* `status`: `EnumType.STRING` NOT NULL (Default: `PENDING`)
* `images`: `@OneToMany` Mối quan hệ với `BugReportImage`

#### Entity `BugReportImage` (Bảng `bug_report_images`)
* `id`: PK (`BaseEntity`)
* `bug_report_id`: FK (`bug_reports.id`) NOT NULL
* `image_url`: `VARCHAR(500)` NOT NULL

---

### 4.2. RESTful APIs

Package: `com.codegym.mathclass.bugreport.controller` (Sử dụng `@ApiVersion(1)`)

| Method | Endpoint | Authorization | Mô tả |
|---|---|---|---|
| `POST` | `/api/bug-reports/public` | PermitAll | Gửi báo cáo công khai từ trang Login. |
| `POST` | `/api/bug-reports` | Authenticated | Gửi báo cáo khi đã đăng nhập (Tự đính kèm User context). |
| `GET` | `/api/admin/bug-reports` | `@PreAuthorize("hasRole('ADMIN')")` | Lấy danh sách báo cáo phân trang. |
| `GET` | `/api/admin/bug-reports/{id}` | `@PreAuthorize("hasRole('ADMIN')")` | Lấy chi tiết báo cáo. |
| `PATCH` | `/api/bug-reports/{id}/status` | `@PreAuthorize("hasRole('ADMIN')")` | Cập nhật trạng thái xử lý (`PENDING`, `IN_PROGRESS`, `RESOLVED`). |

---

## 5. Đặc Tả Frontend (`MathClass-fe`)

### 5.1. File Structure & Components
1. `components/bug-report/ReportBugModal.tsx`: Form Modal báo cáo sự cố (dùng `react-hook-form` + `zod`).
2. `components/layout/sidebar.tsx`:
   - Rename `/reports` (Teacher): `"Báo cáo sử dụng"`.
   - Add `"Báo cáo sự cố"` (Teacher, Student): Mở `ReportBugModal`.
   - Add `"Quản lý Báo cáo lỗi"` (Admin): Route `/admin/bug-reports`.
3. `app/(dashboard)/admin/bug-reports/page.tsx` & `_components/bug-reports-list-client.tsx`: Trang Admin danh sách báo cáo (Table STT, UTC+7, Status Badge, Modal Xem chi tiết).

---

## 6. Kế Hoạch Kiểm Thử (Verification Plan)

### Automated Tests
* **Backend:** `./gradlew compileJava` & `./gradlew test`
* **Frontend:** `npx tsc --noEmit` & `npm run build`

### Manual Test Scenarios
1. Nộp báo cáo không đăng nhập tại trang Login (Nhập email bắt buộc).
2. Nộp báo cáo khi đã đăng nhập (Auto-fill email, tải tối đa 3 ảnh ≤ 5MB).
3. Admin xem danh sách báo cáo (Cột STT, thời gian GMT+7, mở popup chi tiết và đổi trạng thái).
