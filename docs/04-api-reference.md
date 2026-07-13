# Tài liệu API (API Reference)

Tài liệu này liệt kê các API Endpoints quan trọng của hệ thống. (Lưu ý: Môi trường chạy mặc định là `http://localhost:8080`).

## 1. Xác thực (Authentication)

Các API này public (không yêu cầu token).

- `POST /api/auth/register`: Đăng ký tài khoản.
- `POST /api/auth/login`: Đăng nhập, trả về JWT.
- `POST /api/auth/google`: Đăng nhập thông qua Google OAuth2.

## 2. Quản lý Lớp học (Classrooms)

- `POST /api/classrooms/create`: (Role: TEACHER) Tạo mới lớp học.
- `POST /api/classrooms/join`: (Role: STUDENT) Xin vào lớp.

## 3. Quản lý Bài tập (Assignments & Submissions)

- `POST /api/assignments/create`: (Role: TEACHER) Tạo đề bài.
- `PUT /api/assignments/{id}/publish`: (Role: TEACHER) Giao bài tập cho lớp.
- `POST /api/submissions`: (Role: STUDENT) Nộp bài.
- `PUT /api/submissions/{id}/grade`: (Role: TEACHER) Chấm điểm và nhận xét.

## 4. Thống kê (Dashboard)

- `GET /api/dashboard/teacher-stats`: (Role: TEACHER) Xem thống kê lớp dạy.
- `GET /api/dashboard/at-risk-students`: (Role: TEACHER) Lấy danh sách học sinh có nguy cơ rớt (điểm thấp).
- `GET /api/dashboard/student-stats`: (Role: STUDENT) Xem kết quả học tập cá nhân.

*Chú ý: Tất cả các API yêu cầu Role phải đính kèm Header: `Authorization: Bearer <token>`.*
