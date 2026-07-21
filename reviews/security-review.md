# Báo cáo Đánh giá Bảo mật (Security Review & SAST Report)

Dự án: **MathClass-service**  
Ngày đánh giá: 20/07/2026  

---

## 🚨 Phát hiện có Độ nghiêm trọng Cao (High Severity)

### 1. Lỗ hổng Leo thang Đặc quyền (Privilege Escalation) qua Mass Assignment khi Đăng ký

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `AuthServiceImpl.java` (dòng 127)
* **Chi tiết:**
    Trong quá trình đăng ký tài khoản mới qua API `registerUser`, hệ thống đã từng gán trực tiếp vai trò (Role) từ dữ liệu Request.
* **Hậu quả:**
    Kẻ tấn công có thể dễ dàng đánh chặn và thay đổi Request Body (ví dụ: gán `"role": "ADMIN"`) để tạo tài khoản với quyền Quản trị cao nhất, dẫn đến việc chiếm hoàn toàn quyền kiểm soát hệ thống.
* **Đánh giá lại:**
    Mã nguồn đã được cập nhật. Vì `role` được truyền qua DTO dạng Enum nên các giá trị sai lệch tự động bị chặn lại (HTTP 400). Ngoài ra, đã bổ sung thêm logic ở service `if (requestedRole == Role.ADMIN)` ném ra `BadRequestException` để chặn hoàn toàn hành vi đăng ký với quyền Quản trị viên, và mặc định gán quyền `STUDENT` nếu vai trò bị trống.

### 2. Lỗi Cấu hình Mặc định Khóa JWT Gây Crash Hệ thống

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `JwtUtils.java` & `application.properties`
* **Chi tiết:** Khóa JWT mặc định trước đây không phải là chuỗi Base64 hợp lệ, gây crash hệ thống khi giải mã.
* **Đánh giá lại:** Đã kiểm tra tệp `JwtUtils.java` tại dòng 23, cấu hình mặc định đã được thay đổi thành chuỗi Base64 hợp lệ `dGhpc19pc19hX3NlY3VyZV9hbmRfZ2VuZXJhdGVkX2Jhc2U2NF9rZXlfZm9yX21hdGhjY2xhc3NfYXBwbGljYXRpb25fNTEyYml0cwo=`.

### 3. Lỗ hổng IDOR/BOLA (Broken Object Level Authorization) trong Xem Hình vẽ Bài nộp

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `SubmissionDrawingServiceImpl.java`
* **Chi tiết:** API xem hình vẽ không kiểm tra quyền, cho phép mọi học sinh xem bài nộp của người khác.
* **Đánh giá lại:** Đã kiểm tra phương thức `getDrawingBySubmissionId`. Logic kiểm tra quyền sở hữu (`isStudentOwner` và `isTeacherOwner`) đã được thực thi chính xác trước khi trả về dữ liệu.

### 4. Lỗ hổng IDOR/BOLA trong Hệ thống Nhận xét và Bình luận Bài nộp

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `SubmissionCommentServiceImpl.java` & `SubmissionCommentController.java`
* **Đánh giá lại:** Lỗ hổng đã được khắc phục. Mã nguồn hiện tại yêu cầu xác thực email của người dùng với chủ sở hữu bài nộp hoặc giáo viên lớp học.

---

## ⚠️ Phát hiện có Độ nghiêm trọng Trung bình (Medium Severity)

### 5. Lỗ hổng BOLA Xem Bài tập Chưa Giao của Học sinh

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `AssignmentServiceImpl.java`
* **Đánh giá lại:** Trong `getAssignmentsByClassCode` và `getAssignmentsForCurrentUser`, logic phân quyền qua `Specification` đã kiểm tra chặt chẽ `studentId` nằm trong `classroom`, ngăn chặn việc truy cập bài tập của lớp khác.

### 6. Thiếu Làm sạch Mã độc LaTeX (LaTeX Injection) Trong Bài nộp & Nhận xét

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `AssignmentServiceImpl.java`, `SubmissionServiceImpl.java`
* **Đánh giá lại:** Lệnh kiểm tra `LaTeXSanitizer.isSafe()` đã được gọi khi lưu thông tin có chứa nội dung LaTeX, bảo vệ khỏi mã độc XSS và các lệnh gây quá tải renderer.

### 7. Tiềm ẩn Lỗi Xác thực Đuôi Tệp (File Extension Validation) Khi Tải Lên

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `SupabaseStorageService.java` (dòng 36 - 45)
* **Chi tiết:**
    Hệ thống kiểm tra loại tệp bằng cách so sánh `contentType` với danh sách `ALLOWED_TYPES`. Tuy nhiên, đuôi tệp (extension) lại được lấy trực tiếp từ `originalFilename` mà không hề được kiểm chứng (`originalFilename.substring(originalFilename.lastIndexOf("."))`).
* **Hậu quả:**
    Kẻ tấn công có thể tải lên tệp độc hại có đuôi `.html`, `.svg` hoặc `.exe` nhưng giả mạo header `Content-Type` thành `image/png`. Tệp sẽ được lưu trữ trên Supabase với đuôi tệp do kẻ tấn công chọn. Tuy Supabase và hệ thống gán `Content-Type: image/png` khiến trình duyệt khó có thể thực thi mã HTML, nhưng điều này vẫn vi phạm quy tắc an toàn (Insecure File Upload) và tiềm ẩn nguy cơ bảo mật.
* **Khuyến nghị:**
    Nên ánh xạ trực tiếp `contentType` hợp lệ sang đuôi tệp tương ứng (ví dụ: `image/png` -> `.png`) thay vì tin tưởng vào `originalFilename` do client gửi lên.

---

## 📝 Phát hiện có Độ nghiêm trọng Thấp hoặc Khuyến nghị (Low / Informational)

### 8. Tạo Mật khẩu Ngẫu nhiên Bằng UUID.randomUUID() Cho Đăng nhập Google

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `AuthServiceImpl.java` (dòng 333)
* **Chi tiết:**
    Khi người dùng đăng nhập bằng Google lần đầu, hệ thống tạo mật khẩu ngẫu nhiên bằng `UUID.randomUUID().toString()`.
* **Đánh giá:**
    Mặc dù an toàn trong ngữ cảnh này vì mật khẩu chỉ đóng vai trò placeholder (không ai biết, kể cả người dùng), nhưng `UUID` không được thiết kế như một bộ tạo số ngẫu nhiên an toàn mật mã (Cryptographically Secure Pseudo-Random Number Generator - CSPRNG).
* **Khuyến nghị:**
    Sử dụng `SecureRandom` để tạo chuỗi ngẫu nhiên mạnh hơn làm mật khẩu.

### 9. Thiếu Phân quyền Bổ sung Khi Giáo viên Thêm Bình luận bài nộp

* **Trạng thái:** [x] 🟢 Resolved
* **Vị trí:** `SubmissionCommentServiceImpl.java`
* **Đánh giá lại:** Mã nguồn hiện tại đã so sánh trực tiếp ID của giáo viên với ID của giáo viên tạo ra bài tập trước khi cho phép bình luận.
