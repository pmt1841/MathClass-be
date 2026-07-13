# Hướng Dẫn Cài Đặt Môi Trường (Backend Setup Guide)

## 1. Yêu cầu (Prerequisites)

- **Java JDK 21**
- **Docker Desktop** (Đề xuất)
- **IDE:** IntelliJ IDEA hoặc Eclipse (khuyên dùng IntelliJ IDEA).
- **Git**

## 2. Cấu hình Môi trường (.env)

Tạo file `.env` tại thư mục gốc `MathClass-service/`:

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

## 3. Khởi chạy Ứng dụng (Running)

### 3.1 Chạy bằng Docker Compose (Khuyên dùng)

Tại thư mục `MathClass-service/`:

```bash
docker-compose up --build
```

Dịch vụ sẽ tự động setup Database (port 5433) và chạy Spring Boot app (port 8080).

### 3.2 Chạy thủ công

Nếu bạn đã tự cài đặt PostgreSQL và tạo DB khớp với `.env`:

```bash
./gradlew bootRun
```

## 4. Kiểm tra

- Mở trình duyệt truy cập: `http://localhost:8080/api/auth/register` (sẽ trả về lỗi Method Not Supported hoặc form tương ứng, chứng tỏ server đã chạy).
- Để bắt đầu đóng góp mã nguồn, hãy đọc [Hướng dẫn Backend (Backend Guide)](03-backend-guide.md).
