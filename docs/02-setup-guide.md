# Hướng Dẫn Cài Đặt Môi Trường (Backend Setup Guide)

## 1. Yêu cầu Hệ thống (Prerequisites)

- **Java JDK 21**
- **Docker & Docker Desktop** (Khuyên dùng cho PostgreSQL container)
- **IDE:** IntelliJ IDEA (Khuyên dùng) hoặc Eclipse
- **Gradle 8.x+** (Đã đính kèm Gradle Wrapper `gradlew`)
- **Git**

## 2. Cấu hình Môi trường (.env)

Tạo file `.env` tại thư mục gốc của dự án (`MathClass-service/.env`):

```properties
# ==========================================
# Cấu hình Cơ sở dữ liệu PostgreSQL
# ==========================================
# [Cách 1] Dùng khi chạy toàn bộ qua Docker Compose (docker-compose up):
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password
POSTGRES_DB=math_class_db

# [Cách 2] Dùng khi chạy Spring Boot trực tiếp trên máy host (./gradlew bootRun / IntelliJ):
DB_URL=jdbc:postgresql://localhost:5433/math_class_db
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password

# Cấu hình SMTP Gmail gửi email xác thực & thông báo
MAIL_USERNAME=your_gmail@gmail.com
MAIL_PASSWORD=your_app_password

# Cấu hình kết nối Supabase Cloud Storage (upload avatar, ảnh bài tập)
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_KEY=your_supabase_anon_key

# Google OAuth2 Client ID (Đăng nhập Google)
GOOGLE_CLIENT_ID=your_google_client_id.apps.googleusercontent.com

# Cấu hình JWT Secret (Base64 encoded, tối thiểu 512-bit cho HMAC-SHA512)
# Linux / macOS / Git Bash: openssl rand -base64 64
# Windows PowerShell: [Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
JWT_SECRET=your_jwt_secret_key_base64_min_512_bits_here

# Cấu hình Infisical Secret Management (Tùy chọn, mặc định false để chạy local)
INFISICAL_ENABLED=false
INFISICAL_HOST=https://app.infisical.com
INFISICAL_CLIENT_ID=your_machine_identity_client_id
INFISICAL_CLIENT_SECRET=your_machine_identity_client_secret
INFISICAL_PROJECT_ID=your_infisical_project_id
INFISICAL_ENV=dev
INFISICAL_SECRET_PATH=/
INFISICAL_SECRET_NAME=AI_ENCRYPTION_MASTER_KEY
AI_ENCRYPTION_MASTER_KEY=MathClassSecretKeyForAiEncryption32B!
```

> 🔐 **Ghi chú về Secret Management:** Chi tiết hướng dẫn thiết lập Machine Identity và lấy secret key từ Infisical xem tại [Hướng dẫn Sử dụng Infisical Secret Management (08-infisical-secrets-guide.md)](08-infisical-secrets-guide.md).


## 3. Dữ liệu Khởi tạo (Database Seeding)

Dự án hỗ trợ nạp dữ liệu mẫu ban đầu (Data Seeding) tự động khi khởi động.
Trong file `src/main/resources/application.properties`:

```properties
mathclass.seed.enabled=true
```

Khi bật option này, ứng dụng sẽ tự khởi tạo tài khoản mặc định (Admin, Teacher, Student) và dữ liệu bài tập mẫu khi mở kết nối DB lần đầu.

## 4. Khởi chạy Ứng dụng (Running)

### 4.1 Chạy bằng Docker Compose (Khuyên dùng)

Tại thư mục gốc `MathClass-service/`:

```bash
docker-compose up --build
```

Docker Compose sẽ khởi tạo 2 service:
- `db`: PostgreSQL 16 (Port 5433 trên host map vào Port 5432 container).
- `backend`: Spring Boot App (Port 8080).

> Xem chi tiết tài liệu quản lý và vận hành Docker tại [Hướng dẫn Sử dụng Docker (05-docker-guide.md)](05-docker-guide.md).


### 4.2 Chạy thủ công với Gradle

1. Khởi chạy PostgreSQL database (hoặc dùng docker chỉ chạy db: `docker-compose up db -d`).
2. Chạy ứng dụng Spring Boot:

```bash
# Trên Linux / macOS / Git Bash
./gradlew bootRun

# Trên Windows Command Prompt / PowerShell
.\gradlew.bat bootRun
```

## 5. Kiểm tra & Verification

- Server khởi chạy tại: `http://localhost:8080`
- Kiểm tra trạng thái ứng dụng via Spring Actuator: `http://localhost:8080/actuator/health`
- Thử gửi yêu cầu POST đăng nhập: `http://localhost:8080/api/auth/login`
- Đọc tiếp [Hướng dẫn Phát triển (Backend Guide)](03-backend-guide.md) để nắm rõ cấu trúc source code và quy chuẩn lập trình.

