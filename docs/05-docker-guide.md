# Hướng Dẫn Sử Dụng Docker (Docker & Docker Compose Guide)

Tài liệu này cung cấp hướng dẫn chi tiết về cấu hình, khởi chạy, cơ chế Hot-Reload và xử lý sự cố môi trường Docker trong dự án **MathClass-service**.

---

## 1. Kiến Trúc Container (Container Architecture)

Dự án sử dụng **Docker Compose** để quản lý 2 dịch vụ chính:

| Service | Image / Base Stage | Container Name | Port (Host:Container) | Chức năng |
| :--- | :--- | :--- | :--- | :--- |
| **`db`** | `postgres:16-alpine` | `math-class-db` | `5433:5432` | Cơ sở dữ liệu PostgreSQL 16 |
| **`backend`** | `eclipse-temurin:21-jdk` (stage `builder`) | `math-class-backend` | `8080:8080` | Spring Boot API Service |

---

## 2. Chuẩn Bị File Môi Trường (`.env`)

Trước khi khởi chạy Docker Compose, hãy đảm bảo đã tạo file `.env` tại thư mục gốc `MathClass-service/` (tham khảo mẫu trong file [.env.example](../.env.example))

## 3. Các Lệnh Khởi Chạy & Thao Tác Thường Dùng

### 3.1. Khởi chạy toàn bộ hệ thống (Backend + Database)

Chạy ngầm (detached mode):

```bash
docker compose up -d
```

Hoặc build lại image và chạy ngầm (khuyên dùng khi mới pull code hoặc sửa `build.gradle`/`Dockerfile`):

```bash
docker compose up -d --build
```

### 3.2. Chỉ khởi chạy Database (Dành cho việc dev Backend trực tiếp trên IDE)

Nếu bạn muốn chạy backend trực tiếp bằng IntelliJ / Eclipse / CLI local để debug nhanh hơn:

```bash
docker compose up db -d
```

### 3.3. Xem Log của Container

Xem log của tất cả các services:

```bash
docker compose logs -f
```

Chỉ xem log của service Backend:

```bash
docker compose logs -f backend
```

Chỉ xem log của service Database:

```bash
docker compose logs -f db
```

### 3.4. Dừng và Quản lý Container

Dừng tất cả các services (giữ nguyên dữ liệu DB):

```bash
docker compose down
```

Dừng services và **xóa toàn bộ dữ liệu DB volume** (dùng khi muốn reset DB về trạng thái sạch ban đầu):

```bash
docker compose down -v
```

Khởi động lại một service cụ thể (ví dụ: `backend`):

```bash
docker compose restart backend
```

---

## 4. Cơ Chế Hot-Reload (Continuous Build)

Cấu hình trong [docker-compose.yml](../docker-compose.yml) được tối ưu cho môi trường phát triển (Development):

```yaml
volumes:
  - ./src:/app/src
  - ./build.gradle:/app/build.gradle
command: ./gradlew bootRun --continuous
```

### ❓ Có cần phải Build lại hoặc Restart mỗi khi thay đổi code không?

- **KHÔNG CẦN** khi chỉnh sửa code Java thông thường (`.java`, `.properties`, `.xml` trong thư mục `src/`). Cờ `--continuous` của Gradle cùng tính năng bind volume sẽ tự động phát hiện file thay đổi và biên dịch/re-boot ứng dụng ngay trong container.

### ⚠️ Khi nào CẦN hành động?

1. **Cần Rebuild (`docker compose up -d --build`):**
   - Khi thêm/sửa/xóa thư viện (dependencies) trong file `build.gradle`.
   - Khi chỉnh sửa nội dung file [Dockerfile](../Dockerfile).

2. **Cần Restart (`docker compose restart backend`):**
   - Khi thay đổi các biến môi trường trong file `.env`.
   - Khi tiến trình Gradle bên trong container bị treo do lỗi biên dịch nặng.

---

## 5. Xử Lý Sự Cố Thường Gặp (Troubleshooting)

### 5.1. Lỗi Xung Đột Cổng (Port Already in Use)

- **Cổng 8080 (Backend):** Kiểm tra xem có ứng dụng Tomcat/Spring Boot khác đang chạy local không.
- **Cổng 5433 (PostgreSQL Host Port):** Kiểm tra xem dịch vụ PostgreSQL cài trên máy thật hoặc container khác có đang chiếm dụng cổng `5433` hay không.

### 5.2. Backend không kết nối được Database khi khởi chạy

Service `backend` trong `docker-compose.yml` đã được cấu hình `depends_on` kèm `healthcheck`:

```yaml
depends_on:
  db:
    condition: service_healthy
```

Nếu `backend` chưa thể chạy, kiểm tra trạng thái container DB bằng:

```bash
docker compose ps
```

Đảm bảo container `math-class-db` có trạng thái `healthy`.

### 5.3. Lỗi "bad interpreter: \r" hoặc không có quyền chạy `gradlew` trên Linux/macOS

Lỗi này xảy ra khi file `gradlew` được lưu dưới định dạng dòng CRLF của Windows. `Dockerfile` đã tự động xử lý bằng lệnh:

```dockerfile
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
```

Nếu chạy script local không qua Docker, thực hiện lệnh tương tự trên terminal Git Bash/Linux.

### 5.4. Reset dữ liệu về trạng thái khởi tạo mẫu (Data Seeding)

Nếu ứng dụng được cấu hình `mathclass.seed.enabled=true`, bạn có thể reset lại toàn bộ DB về dữ liệu ban đầu bằng lệnh:

```bash
docker compose down -v
docker compose up -d
```
