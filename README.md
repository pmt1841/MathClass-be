# MathClass-service: Dự án Quản lý Lớp học Toán trực tuyến

Hệ thống quản lý học tập (LMS) chuyên biệt dành riêng cho việc dạy và học Toán trực tuyến. Hệ thống hỗ trợ xử lý công thức Toán bằng LaTeX, tích hợp bảng vẽ hình học và đồ thị JSXGraph, cùng khả năng phân tích học tập tự động cho giáo viên.

---

## 🧭 Kiến trúc Hệ thống & Cấu trúc Thư mục

Mã nguồn dự án được tổ chức theo cấu trúc phân tầng chuẩn (**Layered Architecture**):

```
src/main/java/com/codegym/mathclass
 ├── Main Application  <- [MathClassApplication.java](src/main/java/com/codegym/mathclass/MathClassApplication.java)
 ├── auth              <- Quản lý đăng ký, đăng nhập & OAuth2 ([AuthController.java](src/main/java/com/codegym/mathclass/auth/controller/AuthController.java))
 ├── user              <- Thông tin & hồ sơ người dùng ([User.java](src/main/java/com/codegym/mathclass/user/entity/User.java))
 ├── classroom         <- Quản lý lớp học & học sinh ([Classroom.java](src/main/java/com/codegym/mathclass/classroom/entity/Classroom.java))
 ├── assignment        <- Tạo lập đề bài, đề thi toán học ([Assignment.java](src/main/java/com/codegym/mathclass/assignment/entity/Assignment.java))
 ├── submission        <- Học sinh nộp bài giải, giáo viên chấm điểm ([Submission.java](src/main/java/com/codegym/mathclass/submission/entity/Submission.java))
 ├── dashboard         <- Thống kê tiến độ & phát hiện học sinh cần hỗ trợ ([DashboardController.java](src/main/java/com/codegym/mathclass/dashboard/controller/DashboardController.java))
 ├── security          <- Cấu hình bảo mật JWT & phân quyền API ([SecurityConfig.java](src/main/java/com/codegym/mathclass/security/config/SecurityConfig.java))
 ├── config            <- Nạp cấu hình môi trường ([DotenvInitializer.java](src/main/java/com/codegym/mathclass/config/DotenvInitializer.java))
 └── utils             <- Các tiện ích dùng chung (Email, Supabase Storage, LaTeX Sanitizer)
```

---

## 🛠️ Công nghệ Sử dụng

*   **Ngôn ngữ lập trình:** Java 21 (sử dụng [build.gradle](build.gradle))
*   **Framework chính:** Spring Boot (Starter Web, Data JPA, Security, Mail, Validation)
*   **Cơ sở dữ liệu:** PostgreSQL 16 (lưu trữ quan hệ và dữ liệu JSONB)
*   **Xác thực:** JSON Web Tokens (JWT) + Google OAuth2 Client
*   **Lưu trữ đám mây:** Supabase Object Storage (dùng để lưu trữ tài liệu ảnh đính kèm bài viết/đề bài)
*   **Quản lý môi trường:** `dotenv-java` nạp cấu hình từ tệp `.env`
*   **Ảo hóa & Devops:** Docker & Docker Compose (cho phép lập trình viên tự động reload mã nguồn trong container)

---

## 🔑 Các Tính năng Core

### 1. Quản lý Đề bài & Bảo mật Công thức Toán (LaTeX)
*   Nội dung bài tập được lưu trữ dưới dạng text thô chứa công thức toán LaTeX (frontend tự render bằng KaTeX hoặc MathJax).
*   **Bảo mật:** Sử dụng tiện ích [LaTeXSanitizer.java](src/main/java/com/codegym/mathclass/utils/LaTeXSanitizer.java) để chặn mọi mã độc hại nhúng vào các thẻ lệnh LaTeX (như lệnh đọc file hệ thống `\input`, ghi file `\write`, v.v.).

### 2. Tích hợp Bảng Vẽ Hình học (JSXGraph)
*   Học sinh và giáo viên có thể thực hiện vẽ hình trực quan ngay trên hệ thống. Dữ liệu tọa độ hình vẽ được lưu trữ dưới định dạng `jsonb` trong cơ sở dữ liệu (thông qua [AssignmentDrawing.java](src/main/java/com/codegym/mathclass/assignment/entity/AssignmentDrawing.java) & [SubmissionDrawing.java](src/main/java/com/codegym/mathclass/submission/entity/SubmissionDrawing.java)), giúp tối ưu hóa hiệu năng lưu trữ và hiển thị.

### 3. Phân tích Học tập & Dashboard
*   **Dành cho giáo viên:** Thống kê tổng số học sinh, danh sách bài làm chưa chấm và tự động tính toán phát hiện các học sinh học lực yếu hoặc có nguy cơ tụt lại phía sau (`at-risk-students`).
*   **Dành cho học sinh:** Hiển thị biểu đồ kết quả bài làm cá nhân, nhiệm vụ học tập sắp hết hạn (`student-pending-tasks`), và danh sách bài tập đã được chấm điểm kèm theo nhận xét của giáo viên.

---

## ⚙️ Cấu Hình Hệ Thống

Để dự án hoạt động, cần cấu hình các biến môi trường trong tệp `.env` đặt tại thư mục gốc của dự án:

```properties
# Cơ sở dữ liệu PostgreSQL
DB_URL=jdbc:postgresql://localhost:5433/mathclass_db
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password

# Cấu hình SMTP Gmail gửi thông báo
MAIL_USERNAME=your_gmail@gmail.com
MAIL_PASSWORD=your_app_password

# Cấu hình kết nối Supabase Cloud Storage
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_KEY=your_supabase_anon_key

# Google OAuth2 Client ID
GOOGLE_CLIENT_ID=your_google_client_id.apps.googleusercontent.com
```

Ứng dụng sẽ nạp động các thuộc tính trên vào Spring Environment thông qua lớp cấu hình [application.properties](src/main/resources/application.properties).

---

## 🚀 Hướng Dẫn Khởi Chạy (Local Development)

### Sử dụng Docker Compose (Khuyên dùng)
Hệ thống đã được thiết lập sẵn môi trường Docker hoàn chỉnh giúp đồng bộ cơ sở dữ liệu và kích hoạt cơ chế tự động biên dịch lại code backend khi thay đổi file nguồn:

1.  **Khởi động các dịch vụ (PostgreSQL + Spring Boot Backend):**
    ```bash
    docker-compose up --build
    ```
2.  Môi trường backend sẽ chạy ở cổng `8080`, còn cơ sở dữ liệu PostgreSQL sẽ được ánh xạ ra cổng `5433` trên máy local.

### Chạy thủ công bằng Gradle
Nếu bạn muốn tự chạy trực tiếp trên máy vật lý:

1.  **Cài đặt Java JDK 21.**
2.  **Khởi tạo cơ sở dữ liệu PostgreSQL local** khớp với cấu hình trong tệp `.env`.
3.  **Khởi động ứng dụng:**
    ```bash
    ./gradlew bootRun
    ```

---

## 📊 Danh sách API Endpoints Chính

| Phương thức | Endpoint | Phân quyền | Chức năng |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/register` | Public | Đăng ký tài khoản mới |
| **POST** | `/api/auth/login` | Public | Đăng nhập nhận JWT Token |
| **POST** | `/api/auth/google` | Public | Đăng nhập bằng tài khoản Google |
| **POST** | `/api/classrooms/create` | `TEACHER` | Tạo mới một lớp học |
| **POST** | `/api/classrooms/join` | `STUDENT` | Học sinh gửi yêu cầu xin vào lớp |
| **POST** | `/api/assignments/create` | `TEACHER` | Tạo mới đề bài tập toán (DRAFT) |
| **PUT** | `/api/assignments/{id}/publish` | `TEACHER` | Giao bài tập cho lớp học cụ thể |
| **POST** | `/api/submissions` | `STUDENT` | Học sinh gửi bài làm toán |
| **PUT** | `/api/submissions/{id}/grade` | `TEACHER` | Chấp nhận chấm điểm và nhận xét bài nộp |
| **GET** | `/api/dashboard/teacher-stats` | `TEACHER` | Thống kê hiệu suất học tập lớp học |
| **GET** | `/api/dashboard/at-risk-students`| `TEACHER` | Danh sách học sinh học yếu cần hỗ trợ |
| **GET** | `/api/dashboard/student-stats` | `STUDENT` | Thống kê kết quả học tập cá nhân học sinh |
