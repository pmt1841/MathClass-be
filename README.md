# MathClass-service: Nền tảng Làm bài tập Toán Trực tuyến Tích hợp Công cụ Hỗ trợ

**MathClass** là dịch vụ backend cho nền tảng làm bài tập và luyện tập Toán trực tuyến. Hệ thống cung cấp giải pháp toàn diện giúp Giáo viên tạo đề, giao bài và chấm điểm; giúp Học sinh làm bài, nộp bài trực tuyến với sự hỗ trợ của các công cụ chuyên biệt dành riêng cho môn Toán (nhập công thức LaTeX, bảng vẽ hình học Canvas/JSXGraph, bóc tách đề bài từ file DOCX/PDF, nhận xét và thông báo realtime).

---

## 💡 Các Công cụ & Tính năng Nổi bật

1. **Công cụ Soạn thảo & Rendering Công thức Toán (LaTeX)**
   - Hỗ trợ lưu trữ và xử lý các câu hỏi, lời giải chứa công thức Toán học chuẩn LaTeX.
   - **Bảo mật Toán học:** Tích hợp bộ lọc `LaTeXSanitizer` để ngăn chặn các nguy cơ tấn công injection qua thẻ lệnh LaTeX.

2. **Công cụ Vẽ hình & Đồ thị Tương tác (JSXGraph / Canvas)**
   - Cho phép học sinh và giáo viên vẽ hình học, dựng đồ thị ngay trên giao diện bài tập.
   - Dữ liệu tọa độ bản vẽ được tối ưu hóa lưu trữ dưới dạng `jsonb` trong PostgreSQL (`AssignmentDrawing` & `SubmissionDrawing`).

3. **Bóc tách Đề bài Tự động từ File (DOCX / PDF)**
   - Tích hợp thư viện Apache POI & Apache PDFBox giúp giáo viên nhanh chóng trích xuất nội dung câu hỏi từ tài liệu sẵn có để tạo đề bài tập.

4. **Nộp bài, Chấm điểm & Nhận xét Tương tác**
   - Hỗ trợ đa dạng hình thức bài tập (Trắc nghiệm, Tự luận, Bài tập vẽ hình).
   - Cho phép học sinh hủy nộp bài (`unsubmit`) để chỉnh sửa trước hạn, giáo viên chấm điểm, trả nhận xét và bình luận trực tiếp trên từng bài nộp.

5. **Thống kê & Cảnh báo Tiến độ Học tập**
   - **Dành cho Giáo viên:** Báo cáo tổng quan số lượng bài làm, danh sách bài tập chờ chấm, phân tích học sinh có nguy cơ học kém (`at-risk-students`).
   - **Dành cho Học sinh:** Thống kê kết quả học tập cá nhân, theo dõi bài tập sắp tới hạn và lịch sử điểm số.

6. **Thông báo Real-time (Server-Sent Events)**
   - Cập nhật tức thì các sự kiện: bài tập mới được giao, bài làm đã được chấm điểm, nhắc nhở hạn nộp qua luồng SSE (`/api/notifications/stream`).

---

## 🛠️ Công nghệ Sử dụng (Tech Stack)

- **Ngôn ngữ lập trình:** Java 21
- **Framework chính:** Spring Boot 4.1.0 (Spring Web, Spring Data JPA, Spring Security, Spring Mail, Validation, Actuator)
- **Cơ sở dữ liệu:** PostgreSQL 16 (Lưu trữ quan hệ & dữ liệu dạng `jsonb`)
- **Xác thực & Bảo mật:** Spring Security, JJWT (Cookie HTTP-Only & Header Bearer Token), Google OAuth2 Client
- **Document Processing:** Apache POI (DOCX) & Apache PDFBox (PDF)
- **Real-time:** Spring SSE (Server-Sent Events)
- **Cloud Storage:** Supabase Cloud Storage (Lưu trữ avatar và ảnh đính kèm bài tập)
- **Quản lý Môi trường:** `dotenv-java` nạp cấu hình từ `.env`
- **Containerization:** Docker & Docker Compose

---

## 🧭 Cấu trúc Mã nguồn (Project Structure)

```
src/main/java/com/codegym/mathclass/
 ├── auth/         # Xác thực: Đăng nhập/Đăng ký, OTP Email, Google OAuth2, Reset Password
 ├── user/         # Quản lý thông tin cá nhân, Avatar, Admin User & Phân quyền Roles/Permissions
 ├── classroom/    # Quản lý lớp học, duyệt yêu cầu gia nhập (Join Requests) & thành viên lớp
 ├── assignment/   # Tạo đề bài tập Toán, giao bài tập cho lớp & bóc tách đề từ file DOCX/PDF
 ├── submission/   # Học sinh nộp bài, bản vẽ Canvas, giáo viên chấm điểm & bình luận
 ├── dashboard/    # Thống kê hiệu suất làm bài tập, bài nộp chờ chấm & học sinh nguy cơ học yếu
 ├── notification/ # Hệ thống thông báo thời gian thực (SSE Stream) & Cấu hình nhận thông báo
 ├── systemlog/    # Nhật ký hoạt động hệ thống (System Audit Logs) dành cho Admin
 ├── security/     # Cấu hình Spring Security, JWT Filter, CustomUserDetails
 ├── config/       # Nạp biến môi trường Dotenv, CORS, Cache, Async, JPA
 ├── exception/    # Custom Exceptions & Global Exception Handler
 └── utils/        # Các tiện ích (LaTeXSanitizer, Supabase Storage Util, File Parsers)
```

---

## ⚙️ Cấu Hình Môi Trường (.env)

Tạo file `.env` tại thư mục gốc `MathClass-service/`:

```properties
# Cơ sở dữ liệu PostgreSQL
DB_URL=jdbc:postgresql://localhost:5433/mathclass_db
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password

# Cấu hình SMTP Gmail gửi email thông báo & OTP
MAIL_USERNAME=your_gmail@gmail.com
MAIL_PASSWORD=your_app_password

# Cấu hình kết nối Supabase Cloud Storage
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_KEY=your_supabase_anon_key

# Google OAuth2 Client ID
GOOGLE_CLIENT_ID=your_google_client_id.apps.googleusercontent.com

# Cấu hình JWT Secret (Base64 512-bit)
JWT_SECRET=dGhpc19pc19hX3NlY3VyZV9hbmRfZ2VuZXJhdGVkX2Jhc2U2NF9rZXlfZm9yX21hdGhjY2xhc3NfYXBwbGljYXRpb25fNTEyYml0cwo=
```

---

## 🚀 Hướng Dẫn Khởi Chạy (Local Development)

### Cách 1: Sử dụng Docker Compose (Khuyên dùng)

Tự động dựng môi trường PostgreSQL 16 và Spring Boot Backend:

```bash
docker-compose up --build
```

- Backend API: `http://localhost:8080`
- PostgreSQL Database: Port `5433` (trên máy local) map với `5432` (trong container).

### Cách 2: Chạy bằng Gradle Wrapper

1. Đảm bảo đã cài đặt **Java JDK 21**.
2. Khởi chạy PostgreSQL database độc lập theo đúng thông tin trong file `.env`.
3. Kích hoạt ứng dụng:

```bash
# Linux / macOS / Git Bash
./gradlew bootRun

# Windows Command Prompt / PowerShell
.\gradlew.bat bootRun
```

---

## 📊 Danh sách API Endpoints Chính (Prefix: `/api/v1`)

| Phương thức | Endpoint | Access / Role | Mô tả Chức năng |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/auth/login` | Public | Đăng nhập tài khoản & nhận JWT Cookie / Token |
| **POST** | `/api/v1/auth/register` | Public | Đăng ký tài khoản làm bài tập trực tuyến |
| **POST** | `/api/v1/auth/google` | Public | Đăng nhập nhanh bằng tài khoản Google |
| **POST** | `/api/v1/classrooms` | `TEACHER` | Tạo lớp học mới (`201 CREATED`) |
| **POST** | `/api/v1/classrooms/{classCode}/join-requests` | `STUDENT` | Gửi yêu cầu gia nhập lớp bằng mã lớp (`201 CREATED`) |
| **POST** | `/api/v1/assignments` | `TEACHER` | Tạo đề bài tập Toán (`201 CREATED`) |
| **POST** | `/api/v1/assignments/extract-text` | `TEACHER` | Bóc tách câu hỏi bài tập từ file DOCX/PDF |
| **PUT** | `/api/v1/assignments/{id}/publish` | `TEACHER` | Xuất bản & giao bài tập Toán cho lớp học |
| **POST** | `/api/v1/submissions` | `STUDENT` | Học sinh nộp bài làm (`201 CREATED`) |
| **PUT** | `/api/v1/submissions/{id}/unsubmit` | `STUDENT` | Hủy bài nộp để chỉnh sửa lại trước hạn |
| **PUT** | `/api/v1/submissions/{id}/grade` | `TEACHER` | Giáo viên chấm điểm và trả lời nhận xét |
| **PUT** | `/api/v1/submissions/{id}/drawings` | Authenticated | Lưu/cập nhật dữ liệu hình vẽ công cụ Canvas |
| **GET** | `/api/v1/dashboard/teacher-stats` | `TEACHER` | Xem báo cáo tổng quan tình hình bài tập & lớp học |
| **GET** | `/api/v1/dashboard/at-risk-students` | `TEACHER` | Cảnh báo học sinh học yếu/có nguy cơ tụt lại |
| **GET** | `/api/v1/notifications/stream` | Authenticated | Kết nối nhận thông báo real-time qua SSE Stream |

---

## 📚 Tài Liệu API Tương Tác (Swagger / OpenAPI UI)

Ứng dụng tích hợp sẵn tài liệu Swagger UI tự động sinh theo OpenAPI 3.0:

- **Đường dẫn Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Docs:** `http://localhost:8080/v3/api-docs`

### Hướng dẫn thử nghiệm API trên Swagger UI:
1. Khởi chạy Backend và truy cập `http://localhost:8080/swagger-ui.html`.
2. Thực hiện gọi API `POST /api/v1/auth/login` để lấy JWT Token.
3. Bấm nút **Authorize** (ở góc phải màn hình Swagger).
4. Nhập chuỗi JWT Token nhận được (không bao gồm chữ `Bearer `) và chọn **Authorize**.
5. Bây giờ bạn có thể thử nghiệm trực tiếp tất cả các APIs yêu cầu xác thực ngay trên giao diện Swagger.

---

## 📚 Tài Liệu Chi Tiết Trong Thư Mục `docs/`

Để tìm hiểu chi tiết sâu hơn về dự án, vui lòng tham khảo các tài liệu hướng dẫn:

- 📖 [Overview & Business Domains](docs/01-overview.md)
- ⚙️ [Setup Guide & Environment Settings](docs/02-setup-guide.md)
- 💻 [Backend Developer Guide & Coding Conventions](docs/03-backend-guide.md)
- 📑 [Full REST API Reference](docs/04-api-reference.md)
