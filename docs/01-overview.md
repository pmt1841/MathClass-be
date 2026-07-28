# Tổng Quan Dự Án MathClass (Backend)

Chào mừng bạn đến với module Backend của dự án **MathClass**. Hệ thống quản lý học tập (LMS) chuyên sâu cho việc dạy và học Toán trực tuyến.
Tài liệu này đóng vai trò hướng dẫn tổng quan cho các thành viên phát triển Backend nắm bắt kiến trúc và các module chính trong dự án.

## 1. Mục tiêu & Các Module Nghiệp Vụ

Backend chịu trách nhiệm cung cấp toàn bộ RESTful API, xử lý logic nghiệp vụ, bảo mật và lưu trữ dữ liệu cho hệ thống MathClass:

- **Xác thực & Phân quyền (Auth & Security):** Đăng nhập/Đăng ký tài khoản, JWT (dựa trên Cookie HTTP-Only & Header Authorization), Google OAuth2, xác thực Email OTP, Quên/Đặt lại mật khẩu.
- **Quản lý Người dùng & Phân quyền (User & Admin Permission):** Quản lý thông tin cá nhân, avatar, quản lý tài khoản người dùng, phân quyền linh hoạt theo Role (`ADMIN`, `TEACHER`, `STUDENT`) & Fine-grained Permissions.
- **Quản lý Lớp học (Classroom):** Tạo lớp, tìm kiếm, duyệt sinh viên tham gia lớp (Join Requests), quản lý danh sách thành viên trong lớp.
- **Quản lý Bài tập (Assignment):** Tạo bài tập (tự luận & trắc nghiệm, hỗ trợ LaTeX), đính kèm hình vẽ JSXGraph/Canvas, bóc tách đề bài từ file DOCX/PDF, giao bài tập theo lớp và quản lý lịch xuất bản.
- **Nộp bài & Chấm điểm (Submission & Drawing):** Học sinh nộp bài tập (văn bản LaTeX & hình vẽ Canvas tương tác), chấm điểm tự luận/trắc nghiệm, nhận xét câu hỏi, lịch sử nộp và chức năng hủy nộp (Unsubmit).
- **Thống kê & Phân tích (Dashboard):** Tổng quan tình hình giảng dạy của giáo viên, danh sách bài tập chờ chấm, cảnh báo học sinh có nguy cơ học kém (At-risk students), thống kê kết quả học tập của từng học sinh.
- **Thông báo Realtime (Notification & Settings):** Nhận thông báo thời gian thực qua Server-Sent Events (SSE), quản lý cấu hình thông báo (Email, Hệ thống).
- **Nhật ký Hệ thống (System Logs):** Lưu vết audit log các thao tác quan trọng dành cho Quản trị viên.

## 2. Kiến trúc & Công nghệ (Architecture & Tech Stack)

Dự án được xây dựng theo kiến trúc phân tầng (Layered Architecture):

- **Core Framework:** Java 21, Spring Boot 4.1.0.
- **Cơ sở dữ liệu:** PostgreSQL 16 (Dữ liệu quan hệ & kiểu `jsonb` cho tọa độ vẽ hình Canvas / JSXGraph).
- **ORM & Data Access:** Spring Data JPA / Hibernate.
- **Bảo mật:** Spring Security & JJWT (Json Web Token).
- **Real-time Engine:** Spring SSE (Server-Sent Events).
- **Document Parsing:** Apache POI (DOCX) & Apache PDFBox (PDF).
- **File Storage:** Supabase Cloud Storage API.
- **Email Service:** Spring Mail (SMTP Gmail) kết hợp Thymeleaf HTML templates.
- **Caching:** Spring Cache & Caffeine Cache.

## 3. Các Tài Liệu Chi Tiết

Hãy đọc các tài liệu hướng dẫn tiếp theo để bắt tay vào phát triển:

1. [Hướng dẫn Cài đặt Môi trường (Setup Guide)](02-setup-guide.md)
2. [Quy chuẩn và Hướng dẫn Code (Backend Guide)](03-backend-guide.md)
3. [Tài liệu API Chi tiết (API Reference)](04-api-reference.md)
4. [Hướng dẫn Sử dụng Docker (Docker Guide)](05-docker-guide.md)


