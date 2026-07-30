# Tài Liệu API (API Reference)

Tài liệu này tổng hợp toàn bộ danh sách RESTful API Endpoints của hệ thống **MathClass Backend** (Version prefix: `/api/v1`).
Môi trường chạy mặc định: `http://localhost:8080`.

> **Lưu ý xác thực & API Versioning:**
> - Tất cả API đều có tiền tố phiên bản `/api/v1`.
> - Ngoại trừ mục **1. Xác thực (Auth)**, tất cả API đều yêu cầu người dùng đã đăng nhập.
> - Token xác thực có thể được truyền qua Header `Authorization: Bearer <token>` hoặc tự động qua HTTP-Only Cookie (`mathclass_jwt`).
> - Tài liệu Swagger UI trực quan có sẵn tại: `http://localhost:8080/swagger-ui.html`.

---

## 1. Xác thực (Authentication - `/api/v1/auth`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản mới (`201 CREATED`). |
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập bằng Email & Mật khẩu. Trả về Cookie `mathclass_jwt` & `UserInfoResponse`. |
| `POST` | `/api/v1/auth/google` | Public | Đăng nhập/Đăng ký nhanh bằng Google OAuth2 ID Token. |
| `POST` | `/api/v1/auth/logout` | Authenticated | Đăng xuất và vô hiệu hóa Cookie JWT (`mathclass_jwt`). |
| `POST` | `/api/v1/auth/refresh-token` | Public | Cấp lại Access Token mới bằng Refresh Token. |
| `GET` | `/api/v1/auth/verify` | Public | Xác thực mã OTP để kích hoạt tài khoản. |
| `POST` | `/api/v1/auth/forgot-password` | Public | Gửi email chứa link/mã khôi phục mật khẩu. |
| `POST` | `/api/v1/auth/reset-password` | Public | Đặt lại mật khẩu mới. |

---

## 2. Quản lý Người dùng & Phân quyền (`/api/v1/users`, `/api/v1/admin`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/users/me` | Authenticated | Lấy thông tin tài khoản cá nhân đang đăng nhập. |
| `PUT` | `/api/v1/users/me` | Authenticated | Cập nhật thông tin cá nhân. |
| `POST` | `/api/v1/users/me/avatar` | Authenticated | Upload ảnh đại diện mới lên Supabase Cloud Storage. |
| `GET` | `/api/v1/admin/users` | `ADMIN` | Phân trang, lọc & tìm kiếm danh sách người dùng. |
| `PATCH` | `/api/v1/admin/users/{id}/status` | `ADMIN` | Khóa hoặc mở khóa tài khoản người dùng. |
| `GET` | `/api/v1/admin/roles/permissions` | `ADMIN` | Danh sách toàn bộ Permissions trong hệ thống. |
| `GET` | `/api/v1/admin/roles/{roleName}/permissions` | `ADMIN` | Xem danh sách quyền gắn với một Role cụ thể. |
| `PUT` | `/api/v1/admin/roles/{roleName}/permissions` | `ADMIN` | Cập nhật danh sách quyền cho Role. |

---

## 3. Quản lý Lớp học (Classrooms - `/api/v1/classrooms`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/classrooms` | `TEACHER` | Tạo mới một lớp học (`201 CREATED`). |
| `GET` | `/api/v1/classrooms` | Authenticated | Danh sách các lớp học của tôi (Lớp giảng dạy / Lớp tham gia). |
| `GET` | `/api/v1/classrooms/{classCode}` | Authenticated | Lấy chi tiết thông tin lớp học theo mã lớp. |
| `PUT` | `/api/v1/classrooms/{classCode}` | `TEACHER` | Cập nhật thông tin lớp học. |
| `DELETE` | `/api/v1/classrooms/{classCode}` | `TEACHER` | Xóa lớp học (`204 NO CONTENT`). |
| `POST` | `/api/v1/classrooms/join-requests` | `STUDENT` | Gửi yêu cầu gia nhập lớp học bằng mã lớp (`201 CREATED`). |
| `GET` | `/api/v1/classrooms/join-requests/me` | `STUDENT` | Xem danh sách yêu cầu gia nhập lớp cá nhân. |
| `GET` | `/api/v1/classrooms/{classCode}/join-requests` | `TEACHER` | Lấy danh sách yêu cầu chờ duyệt của lớp học. |
| `PUT` | `/api/v1/classrooms/join-requests/{requestId}` | `TEACHER` | Chấp nhận hoặc từ chối yêu cầu gia nhập lớp. |
| `POST` | `/api/v1/classrooms/{classCode}/students` | `TEACHER` | Thêm trực tiếp học sinh vào lớp qua Email. |
| `GET` | `/api/v1/classrooms/{classCode}/students` | Authenticated | Lấy danh sách học sinh thành viên trong lớp. |
| `DELETE` | `/api/v1/classrooms/{classCode}/students/{studentId}` | `TEACHER` | Xóa học sinh ra khỏi lớp học (`204 NO CONTENT`). |

---

## 4. Quản lý Bài tập (Assignments - `/api/v1/assignments`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/assignments` | `TEACHER` | Tạo mới bài tập (`201 CREATED`). |
| `GET` | `/api/v1/assignments` | `TEACHER` | Lấy danh sách tất cả bài tập do giáo viên khởi tạo. |
| `GET` | `/api/v1/assignments/{id}` | Authenticated | Lấy chi tiết nội dung bài tập (Câu hỏi, LaTeX, hình vẽ). |
| `PUT` | `/api/v1/assignments/{id}` | `TEACHER` | Cập nhật nội dung bài tập. |
| `DELETE` | `/api/v1/assignments/{id}` | `TEACHER` | Xóa bài tập (`204 NO CONTENT`). |
| `PUT` | `/api/v1/assignments/{id}/publish` | `TEACHER` | Xuất bản / Giao bài tập cho các lớp học kèm hạn nộp. |
| `POST` | `/api/v1/assignments/images` | `TEACHER` | Upload hình ảnh minh họa cho câu hỏi bài tập. |
| `POST` | `/api/v1/assignments/extract-text` | `TEACHER` | Bóc tách tự động câu hỏi từ file đính kèm DOCX / PDF. |
| `GET` | `/api/v1/classrooms/{classCode}/assignments` | Authenticated | Lấy danh sách bài tập đã được giao trong một lớp. |
| `GET` | `/api/v1/classrooms/{classCode}/assignments/{id}` | Authenticated | Lấy chi tiết bài tập theo lớp. |

---

## 5. Nộp bài & Chấm điểm (Submissions - `/api/v1/submissions`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/submissions` | `STUDENT` | Nộp bài tập (`201 CREATED`). |
| `GET` | `/api/v1/submissions/{submissionId}` | Authenticated | Xem chi tiết bài nộp. |
| `PUT` | `/api/v1/submissions/{submissionId}` | `STUDENT` | Cập nhật bài nộp trước khi hết hạn. |
| `PUT` | `/api/v1/submissions/{submissionId}/unsubmit` | `STUDENT` | Hủy nộp bài để sửa lại (nếu bài chưa được chấm). |
| `PUT` | `/api/v1/submissions/{submissionId}/grade` | `TEACHER` | Chấm điểm bài nộp và gửi nhận xét. |
| `GET` | `/api/v1/submissions/me` | `STUDENT` | Lấy bài nộp cá nhân theo bài tập. |
| `GET` | `/api/v1/submissions` | `TEACHER` | Lấy danh sách bài nộp của một bài tập để giáo viên chấm điểm. |
| `GET` | `/api/v1/submissions/{submissionId}/comments` | Authenticated | Xem các bình luận/nhận xét trên bài nộp. |
| `POST` | `/api/v1/submissions/{submissionId}/comments` | Authenticated | Thêm bình luận mới vào bài nộp (`201 CREATED`). |
| `DELETE` | `/api/v1/submissions/{submissionId}/comments/{commentId}` | Authenticated | Xóa bình luận bài nộp (`204 NO CONTENT`). |
| `GET` | `/api/v1/submissions/{submissionId}/drawings` | Authenticated | Lấy dữ liệu bản vẽ Canvas/JSXGraph gắn liền bài nộp. |
| `PUT` | `/api/v1/submissions/{submissionId}/drawings` | Authenticated | Cập nhật/Lưu dữ liệu bản vẽ Canvas. |

---

## 6. Thống kê & Phân tích (Dashboard - `/api/v1/dashboard`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/dashboard/teacher-stats` | `TEACHER` | Báo cáo tổng quan số lượng lớp, học sinh, bài tập của giáo viên. |
| `GET` | `/api/v1/dashboard/pending-submissions` | `TEACHER` | Danh sách các bài nộp chưa chấm cần xử lý. |
| `GET` | `/api/v1/dashboard/at-risk-students` | `TEACHER` | Phân tích & cảnh báo danh sách học sinh có nguy cơ học lực yếu. |
| `GET` | `/api/v1/dashboard/student-stats` | `STUDENT` | Thống kê kết quả học tập, điểm trung bình & tiến độ của học sinh. |

---

## 7. Thông báo & Cấu hình (`/api/v1/notifications`, `/api/v1/settings`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/notifications/stream` | Authenticated | Kết nối nhận thông báo realtime dạng Server-Sent Events (SSE). |
| `GET` | `/api/v1/notifications` | Authenticated | Lấy danh sách lịch sử thông báo cá nhân (có phân trang). |
| `GET` | `/api/v1/notifications/unread-count` | Authenticated | Số lượng thông báo chưa đọc. |
| `PATCH` | `/api/v1/notifications/read-all` | Authenticated | Đánh dấu toàn bộ thông báo là đã đọc. |
| `PATCH` | `/api/v1/notifications/{id}/read` | Authenticated | Đánh dấu 1 thông báo cụ thể là đã đọc. |
| `GET` | `/api/v1/settings/notifications` | Authenticated | Xem cấu hình bật/tắt nhận thông báo qua Email & Hệ thống. |
| `PUT` | `/api/v1/settings/notifications` | Authenticated | Cập nhật cấu hình nhận thông báo. |

---

## 8. Nhật ký Hệ thống (System Logs - `/api/v1/admin/logs`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/logs` | `ADMIN` | Xem & tra cứu danh sách System Audit Logs của hệ thống. |
