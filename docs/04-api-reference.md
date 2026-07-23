# Tài Liệu API (API Reference)

Tài liệu này tổng hợp toàn bộ danh sách RESTful API Endpoints của hệ thống **MathClass Backend**.
Môi trường chạy mặc định: `http://localhost:8080`.

> **Lưu ý xác thực:**
> Tất cả API Ngoại trừ mục **1. Xác thực (Auth)** đều yêu cầu đã đăng nhập.
> Token xác thực có thể được truyền qua Header `Authorization: Bearer <token>` hoặc tự động qua HTTP-Only Cookie (`mathclass_jwt`).

---

## 1. Xác thực (Authentication - `/api/auth`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Đăng ký tài khoản mới (Gửi OTP qua email). |
| `POST` | `/api/auth/login` | Public | Đăng nhập bằng Email & Mật khẩu (Dùng chung cho Học sinh, Giáo viên và Admin tại `/admin/login`). Trả về Cookie `mathclass_jwt` & vai trò người dùng. |
| `POST` | `/api/auth/google` | Public | Đăng nhập/Đăng ký nhanh bằng Google OAuth2 ID Token. |
| `POST` | `/api/auth/logout` | Authenticated | Đăng xuất và vô hiệu hóa Cookie JWT (`mathclass_jwt`). |
| `POST` | `/api/auth/refreshtoken` | Public | Cấp lại Access Token mới bằng Refresh Token. |
| `GET` | `/api/auth/verify` | Public | Xác thực mã OTP để kích hoạt tài khoản. |
| `POST` | `/api/auth/forgot-password` | Public | Gửi email chứa link/mã khôi phục mật khẩu. |
| `POST` | `/api/auth/reset-password` | Public | Đặt lại mật khẩu mới. |

---

## 2. Quản lý Người dùng & Phân quyền (`/api/users`, `/api/admin`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/users/profile` | Authenticated | Lấy thông tin tài khoản cá nhân đang đăng nhập. |
| `PUT` | `/api/users/profile` | Authenticated | Cập nhật thông tin cá nhân (họ tên, ngày sinh, sđt,...). |
| `POST` | `/api/users/avatar` | Authenticated | Upload ảnh đại diện mới lên Supabase Cloud Storage. |
| `GET` | `/api/admin/users` | `ADMIN` | Phân trang, lọc & tìm kiếm danh sách người dùng. |
| `PATCH` | `/api/admin/users/{id}/status` | `ADMIN` | Khóa hoặc mở khóa tài khoản người dùng. |
| `GET` | `/api/admin/roles/permissions` | `ADMIN` | Danh sách toàn bộ Permissions trong hệ thống. |
| `GET` | `/api/admin/roles/{roleName}/permissions` | `ADMIN` | Xem danh sách quyền gắn với một Role cụ thể. |
| `PUT` | `/api/admin/roles/{roleName}/permissions` | `ADMIN` | Cập nhật danh sách quyền cho Role. |

---

## 3. Quản lý Lớp học (Classrooms - `/api/classrooms`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/classrooms/create` | `TEACHER` | Tạo mới một lớp học. |
| `GET` | `/api/classrooms/my-classroom` | Authenticated | Danh sách các lớp học của tôi (Lớp giảng dạy / Lớp tham gia). |
| `GET` | `/api/classrooms/{classCode}` | Authenticated | Lấy chi tiết thông tin lớp học theo mã lớp. |
| `PUT` | `/api/classrooms/{classCode}` | `TEACHER` | Cập nhật thông tin lớp học. |
| `DELETE` | `/api/classrooms/{classCode}` | `TEACHER` | Xóa lớp học. |
| `POST` | `/api/classrooms/join` | `STUDENT` | Gửi yêu cầu gia nhập lớp học bằng mã lớp (`classCode`). |
| `GET` | `/api/classrooms/my-join-requests` | `STUDENT` | Xem danh sách yêu cầu gia nhập lớp cá nhân. |
| `GET` | `/api/classrooms/{classCode}/join-requests` | `TEACHER` | Lấy danh sách yêu cầu chờ duyệt của lớp học. |
| `PUT` | `/api/classrooms/join-requests/{requestId}` | `TEACHER` | Chấp nhận hoặc từ chối yêu cầu gia nhập lớp. |
| `POST` | `/api/classrooms/{classCode}/students/add` | `TEACHER` | Thêm trực tiếp học sinh vào lớp qua Email/ID. |
| `GET` | `/api/classrooms/{classCode}/students` | Authenticated | Lấy danh sách học sinh thành viên trong lớp. |
| `DELETE` | `/api/classrooms/{classCode}/students/{studentId}` | `TEACHER` | Xóa học sinh ra khỏi lớp học. |

---

## 4. Quản lý Bài tập (Assignments - `/api/assignments`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/assignments/create` | `TEACHER` | Tạo mới bài tập (Draft/Ngân hàng đề). |
| `GET` | `/api/assignments` | `TEACHER` | Lấy danh sách tất cả bài tập do giáo viên khởi tạo. |
| `GET` | `/api/assignments/{id}` | Authenticated | Lấy chi tiết nội dung bài tập (Câu hỏi, LaTeX, hình vẽ). |
| `PUT` | `/api/assignments/{id}` | `TEACHER` | Cập nhật nội dung bài tập. |
| `DELETE` | `/api/assignments/{id}` | `TEACHER` | Xóa bài tập. |
| `PUT` | `/api/assignments/{id}/publish` | `TEACHER` | Xuất bản / Giao bài tập cho các lớp học kèm hạn nộp. |
| `POST` | `/api/assignments/images/upload` | `TEACHER` | Upload hình ảnh minh họa cho câu hỏi bài tập. |
| `POST` | `/api/assignments/extract-text` | `TEACHER` | Bóc tách tự động câu hỏi từ file đính kèm DOCX / PDF. |
| `GET` | `/api/classrooms/{classCode}/assignments` | Authenticated | Lấy danh sách bài tập đã được giao trong một lớp. |

---

## 5. Nộp bài & Chấm điểm (Submissions - `/api/submissions`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/submissions` | `STUDENT` | Nộp bài tập (bao gồm đáp án trắc nghiệm, bài tự luận, tọa độ vẽ). |
| `GET` | `/api/submissions/{submissionId}` | Authenticated | Xem chi tiết bài nộp. |
| `PUT` | `/api/submissions/{submissionId}` | `STUDENT` | Cập nhật bài nộp trước khi hết hạn. |
| `PUT` | `/api/submissions/{submissionId}/unsubmit` | `STUDENT` | Hủy nộp bài để sửa lại (nếu bài chưa được chấm). |
| `PUT` | `/api/submissions/{submissionId}/grade` | `TEACHER` | Chấm điểm bài nộp và gửi nhận xét. |
| `GET` | `/api/submissions/my-submission` | `STUDENT` | Lấy bài nộp cá nhân theo bài tập. |
| `GET` | `/api/submissions` | `TEACHER` | Lấy danh sách bài nộp của một bài tập để giáo viên chấm điểm. |
| `GET` | `/api/submissions/{submissionId}/comments` | Authenticated | Xem các bình luận/nhận xét trên bài nộp. |
| `POST` | `/api/submissions/{submissionId}/comments` | Authenticated | Thêm bình luận mới vào bài nộp. |
| `DELETE` | `/api/submissions/{submissionId}/comments/{commentId}` | Authenticated | Xóa bình luận bài nộp. |
| `GET` | `/api/submissions/{submissionId}/drawings` | Authenticated | Lấy dữ liệu bản vẽ Canvas/JSXGraph gắn liền bài nộp. |
| `PUT` | `/api/submissions/{submissionId}/drawings` | Authenticated | Cập nhật/Lưu dữ liệu bản vẽ Canvas. |

---

## 6. Thống kê & Phân tích (Dashboard - `/api/dashboard`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/dashboard/teacher-stats` | `TEACHER` | Báo cáo tổng quan số lượng lớp, học sinh, bài tập của giáo viên. |
| `GET` | `/api/dashboard/pending-submissions` | `TEACHER` | Danh sách các bài nộp chưa chấm cần xử lý. |
| `GET` | `/api/dashboard/at-risk-students` | `TEACHER` | Phân tích & cảnh báo danh sách học sinh có nguy cơ học lực yếu. |
| `GET` | `/api/dashboard/student-stats` | `STUDENT` | Thống kê kết quả học tập, điểm trung bình & tiến độ của học sinh. |

---

## 7. Thông báo & Cấu hình (`/api/notifications`, `/api/settings`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/notifications/stream` | Authenticated | Kết nối nhận thông báo realtime dạng Server-Sent Events (SSE). |
| `GET` | `/api/notifications` | Authenticated | Lấy danh sách lịch sử thông báo cá nhân (có phân trang). |
| `GET` | `/api/notifications/unread-count` | Authenticated | Số lượng thông báo chưa đọc. |
| `PUT` | `/api/notifications/read-all` | Authenticated | Đánh dấu toàn bộ thông báo là đã đọc. |
| `PUT` | `/api/notifications/{id}/read` | Authenticated | Đánh dấu 1 thông báo cụ thể là đã đọc. |
| `GET` | `/api/settings/notifications` | Authenticated | Xem cấu hình bật/tắt nhận thông báo qua Email & Hệ thống. |
| `PUT` | `/api/settings/notifications` | Authenticated | Cập nhật cấu hình nhận thông báo. |

---

## 8. Nhật ký Hệ thống (System Logs - `/api/admin/logs`)

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/logs` | `ADMIN` | Xem & tra cứu danh sách System Audit Logs của hệ thống. |

