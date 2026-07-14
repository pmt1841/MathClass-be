# Báo cáo Đánh giá Phân quyền (Authorization Review Report)

Dự án: **MathClass-service**  
Ngày đánh giá: 13/07/2026  

---

## 🚨 Phát hiện có Độ nghiêm trọng Cao (High Severity)

### 1. Lỗi Privilege Escalation (Leo thang đặc quyền) trong Đăng ký tài khoản
*   **Trạng thái:** [ ] 🔴 Open
*   **Vị trí:** [AuthServiceImpl.java](../src/main/java/com/codegym/mathclass/auth/service/impl/AuthServiceImpl.java#L90) & [SignupRequest.java](../src/main/java/com/codegym/mathclass/auth/dto/request/SignupRequest.java#L30-L31)
*   **Chi tiết:** 
    Khi đăng ký tài khoản qua API công khai `/api/auth/signup`, hệ thống gán trực tiếp vai trò từ request payload mà không qua kiểm duyệt:
    ```java
    user.setRole(signUpRequest.getRole());
    ```
*   **Hậu quả:** 
    Một kẻ tấn công bên ngoài có thể gửi request payload chứa `"role": "ADMIN"` hoặc `"role": "TEACHER"` để tự cấp quyền Quản trị viên hoặc Giáo viên cho tài khoản tự đăng ký của mình, từ đó truy cập các chức năng quản lý lớp học và chấm điểm bài làm trái phép.
*   **Khuyến nghị khắc phục:**
    Không cho phép đăng ký trực tiếp vai trò `ADMIN` hoặc `TEACHER` qua API signup công khai. Chỉ cho phép tự đăng ký vai trò `STUDENT` hoặc đưa ra cơ chế kiểm duyệt xác minh trước khi kích hoạt tài khoản Giáo viên.
    ```java
    if (signUpRequest.getRole() == Role.ADMIN || signUpRequest.getRole() == Role.TEACHER) {
        throw new BadRequestException("Không thể tự đăng ký vai trò này. Vui lòng liên hệ Admin!");
    }
    user.setRole(signUpRequest.getRole());
    ```

### 2. Lỗ hổng IDOR/BOLA tại API Truy vấn Thông tin cá nhân người dùng
*   **Trạng thái:** [x] 🟢 Resolved
*   **Vị trí:** [UserController.java](../src/main/java/com/codegym/mathclass/user/controller/UserController.java)
*   **Chi tiết:** 
    Đã xử lý triệt để bằng cách xóa hoàn toàn API `/api/users/{id}` do không được sử dụng ở Frontend, tuân thủ nguyên tắc YAGNI và giảm thiểu bề mặt tấn công.

---

## ⚠️ Phát hiện có Độ nghiêm trọng Trung bình (Medium Severity)

### 3. Thiết lập Cơ chế Phân quyền Mặc định Fail-Open (Bảo mật mặc định chưa tối ưu)
*   **Trạng thái:** [ ] 🔴 Open
*   **Vị trí:** [SecurityConfig.java](../src/main/java/com/codegym/mathclass/security/config/SecurityConfig.java#L69-L83)
*   **Chi tiết:**
    Cấu hình bảo mật HTTP sử dụng quy tắc cuối là `.anyRequest().authenticated()` và phụ thuộc hoàn toàn vào `@PreAuthorize` ở Controller để kiểm tra vai trò:
    ```java
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/error").permitAll()
            .anyRequest().authenticated());
    ```
*   **Hậu quả:**
    Nếu lập trình viên thêm một Controller hoặc một API mới mà quên khai báo annotation `@PreAuthorize("hasRole('TEACHER')")`, Spring Security sẽ mặc định cho phép **tất cả người dùng đã đăng nhập** (bao gồm cả Học sinh) truy cập và thực hiện thao tác.
*   **Khuyến nghị khắc phục:**
    Quy hoạch các endpoint theo tiền tố URL (ví dụ: `/api/teacher/**`, `/api/admin/**`) và cấu hình tập trung phân quyền trong `SecurityConfig.java` để đảm bảo cơ chế fail-secure (mặc định từ chối nếu không đúng vai trò).
    ```java
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/error").permitAll()
            .requestMatchers("/api/teacher/**").hasRole("TEACHER")
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated());
    ```

---

## 📝 Phát hiện có Độ nghiêm trọng Thấp hoặc Khuyến nghị (Low / Informational)

### 4. Định nghĩa Vai trò ADMIN nhưng Chưa Sử dụng để Phân quyền
*   **Trạng thái:** [ ] 🔴 Open
*   **Vị trí:** [Role.java](../src/main/java/com/codegym/mathclass/user/entity/Role.java#L4) & [DatabaseSeeder.java](../src/main/java/com/codegym/mathclass/config/DatabaseSeeder.java#L87)
*   **Chi tiết:**
    Hệ thống định nghĩa vai trò `ADMIN` và có khởi tạo tài khoản quản trị viên hệ thống `admin@mathclass.com` khi seed database, nhưng chưa có API nào trong mã nguồn thực tế kiểm tra quyền `ADMIN`.
*   **Hậu quả:**
    Thiếu đi sự phân tách các chức năng dành riêng cho quản trị viên (ví dụ: quản lý danh sách giáo viên, thống kê hệ thống toàn cục).
*   **Khuyến nghị:**
    Cần xây dựng phân hệ quản trị admin riêng biệt và áp dụng cấu hình `.requestMatchers("/api/admin/**").hasRole("ADMIN")` ở cấu hình bảo mật.
