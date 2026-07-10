# Báo cáo Đánh giá Bảo mật (Security Review Report)

Dự án: **MathClass-service**  
Ngày đánh giá: 10/07/2026  

---

## 🚨 Phát hiện có Độ nghiêm trọng Cao (High Severity)

### 1. Lỗi Cấu hình Mặc định Khóa JWT Gây Crash Hệ thống
*   **Vị trí:** [JwtUtils.java](../src/main/java/com/codegym/mathclass/security/jwt/JwtUtils.java#L23-L24) & [application.properties](../src/main/resources/application.properties)
*   **Chi tiết:** 
    Khóa JWT mặc định được cấu hình là:
    `@Value("${mathclass.app.jwtSecret:======================MathClass=Spring=Boot=Secret=Key======================}")`
    Khi sinh hoặc xác thực JWT token, phương thức `Decoders.BASE64.decode(jwtSecret)` được gọi. Tuy nhiên, chuỗi mặc định trên chứa ký tự `=` ở vị trí không hợp lệ đối với bảng mã Base64 (ký tự `=` chỉ được làm padding ở cuối chuỗi).
*   **Hậu quả:** 
    Hệ thống sẽ ném ra ngoại lệ `IllegalArgumentException: Illegal base64 character 3d` và gặp lỗi dừng hoạt động (crash) ngay khi người dùng đăng nhập hoặc đăng ký lần đầu tiên nếu biến môi trường `mathclass.app.jwtSecret` chưa được đặt hoặc cấu hình sai.
*   **Khắc phục đề xuất:**
    1. Cấu hình biến `mathclass.app.jwtSecret=${JWT_SECRET}` trong [application.properties](../src/main/resources/application.properties) để lấy trực tiếp từ file cấu hình môi trường `.env`.
    2. Thêm khóa `JWT_SECRET` với giá trị Base64 hợp lệ và có độ dài bảo mật tối thiểu (ví dụ: tối thiểu 256 bits đối với HS256) vào [.env.example](../.env.example).

### 2. Lỗ hổng IDOR/BOLA (Broken Object Level Authorization) trong Xem Hình vẽ Bài nộp
*   **Vị trí:** [SubmissionDrawingServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/SubmissionDrawingServiceImpl.java#L56-L70) & [SubmissionDrawingController.java](../src/main/java/com/codegym/mathclass/submission/controller/SubmissionDrawingController.java#L39-L51)
*   **Chi tiết:** 
    Phương thức `getDrawingBySubmissionId(long submissionId, String currentUserUsername)` tìm kiếm hình vẽ dựa trên `submissionId` nhưng **không thực hiện bất kỳ kiểm tra quyền nào** đối với `currentUserUsername`.
*   **Hậu quả:** 
    Bất kỳ người dùng nào đã đăng nhập (bao gồm cả học sinh lớp khác) cũng có thể lấy được dữ liệu tọa độ hình vẽ và metadata của bất kỳ bài nộp nào bằng cách đoán ID bài nộp.
*   **Khắc phục đề xuất:**
    Thêm logic kiểm tra xem người dùng hiện tại có phải là chủ nhân của bài nộp (`submission.getStudent().getEmail().equals(currentUserUsername)`) hoặc là giáo viên của bài tập đó (`submission.getAssignment().getTeacher().getEmail().equals(currentUserUsername)`) hay không trước khi trả về dữ liệu hình vẽ.

### 3. Lỗ hổng IDOR/BOLA trong Hệ thống Nhận xét và Bình luận Bài nộp
*   **Vị trí:** [SubmissionCommentServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/impl/SubmissionCommentServiceImpl.java#L32-L42) & [SubmissionCommentController.java](../src/main/java/com/codegym/mathclass/submission/controller/SubmissionCommentController.java#L24-L31)
*   **Chi tiết:** 
    API lấy danh sách bình luận của bài nộp `/api/submissions/{submissionId}/comments` kiểm tra quyền bằng `@PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")` nhưng trong tầng service không kiểm tra xem học sinh hay giáo viên đó có liên quan đến bài nộp đó hay không.
*   **Hậu quả:** 
    Học sinh có thể xem được tất cả các nhận xét, bình luận và đánh giá chi tiết bài nộp của học sinh khác trong hệ thống.
*   **Khắc phục đề xuất:**
    Ràng buộc quyền truy cập:
    *   Học sinh chỉ được xem bình luận của bài nộp do chính mình tạo ra (`submission.getStudent().getId() == currentUserId`).
    *   Giáo viên chỉ được xem bình luận của các bài nộp thuộc bài tập do mình giao (`submission.getAssignment().getTeacher().getId() == currentUserId`).

---

## ⚠️ Phát hiện có Độ nghiêm trọng Trung bình (Medium Severity)

### 4. Lỗ hổng BOLA Xem Bài tập Chưa Giao của Học sinh
*   **Vị trí:** [AssignmentServiceImpl.java](../src/main/java/com/codegym/mathclass/assignment/service/impl/AssignmentServiceImpl.java#L391-L398)
*   **Chi tiết:**
    Phương thức `getAssignmentById` khi kiểm tra vai trò học sinh chỉ kiểm tra điều kiện bài tập đã được chuyển sang trạng thái `PUBLISHED` và không bị `null` lớp học:
    ```java
    if (assignment.getStatus() != AssignmentStatus.PUBLISHED) { ... }
    if (assignment.getClassroom() == null) { ... }
    ```
    Hệ thống không kiểm tra xem học sinh đó có thực sự là thành viên của lớp học chứa bài tập đó hay không.
*   **Hậu quả:**
    Học sinh có thể lấy thông tin chi tiết các đề bài tập của các lớp học khác mà mình không tham gia.
*   **Khắc phục đề xuất:**
    Thêm kiểm tra xem học sinh hiện tại có nằm trong danh sách học sinh của lớp học được giao bài tập hay không:
    ```java
    boolean isEnrolled = assignment.getClassroom().getStudents().stream()
            .anyMatch(s -> s.getId() == userId);
    if (!isEnrolled) {
        throw new AccessDeniedException("Bạn không phải thành viên lớp học được giao bài tập này");
    }
    ```

### 5. Thiếu Làm sạch Mã độc LaTeX (LaTeX Injection) Trong Bài nộp & Nhận xét
*   **Vị trí:** [SubmissionServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/SubmissionServiceImpl.java)
*   **Chi tiết:**
    Hệ thống đã sử dụng [LaTeXSanitizer.java](../src/main/java/com/codegym/mathclass/utils/LaTeXSanitizer.java) để lọc mã độc hại đối với bài tập do giáo viên tạo ra. Tuy nhiên, bài làm của học sinh (`Submission.content`) và nhận xét của giáo viên (`Submission.teacherFeedback` hoặc `SubmissionComment.content`) cũng chứa công thức Toán và cho phép hiển thị dạng LaTeX nhưng không đi qua bộ lọc này.
*   **Hậu quả:**
    Học sinh hoặc giáo viên có thể chèn các lệnh LaTeX nguy hiểm để thực hiện các cuộc tấn công XSS hoặc gây lỗi bộ phân tích cú pháp LaTeX (parser/renderer) ở phía Client.
*   **Khắc phục đề xuất:**
    Bổ sung bước kiểm tra và lọc mã độc bằng `LaTeXSanitizer.isSafe()` cho cả nội dung bài làm khi nộp/cập nhật và nội dung phản hồi/nhận xét trước khi ghi nhận vào cơ sở dữ liệu.

---

## 📝 Phát hiện có Độ nghiêm trọng Thấp hoặc Khuyến nghị (Low / Informational)

### 6. Tạo Mật khẩu Ngẫu nhiên Bằng UUID.randomUUID() Cho Đăng nhập Google
*   **Vị trí:** [AuthServiceImpl.java](../src/main/java/com/codegym/mathclass/auth/service/impl/AuthServiceImpl.java#L199)
*   **Chi tiết:**
    Đối với người dùng mới đăng nhập lần đầu bằng Google, hệ thống tự tạo mật khẩu ngẫu nhiên: `user.setPassword(encoder.encode(java.util.UUID.randomUUID().toString()))`.
*   **Đánh giá:**
    Cách làm này khá an toàn vì mật khẩu được mã hóa và không được trả về hay gửi đi nơi khác. Tuy nhiên, UUID có độ hỗn loạn (entropy) thấp hơn so với việc sinh một mảng byte ngẫu nhiên thông qua `SecureRandom`.
*   **Khuyến nghị:**
    Nên sử dụng `SecureRandom` để tạo chuỗi ký tự ngẫu nhiên mạnh mẽ làm mật khẩu tạm thời cho tài khoản Google OAuth2.

### 7. Thiếu Phân quyền Bổ sung Khi Giáo viên Thêm Bình luận bài nộp
*   **Vị trí:** [SubmissionCommentServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/impl/SubmissionCommentServiceImpl.java#L46-L65)
*   **Chi tiết:**
    Phương thức `addComment` cho phép bất kỳ người dùng nào có vai trò `TEACHER` thêm nhận xét mà không xác thực giáo viên đó có phải chủ sở hữu bài tập chứa bài nộp hay không.
*   **Khắc phục đề xuất:**
    Thêm xác thực tương tự như các phương thức khác:
    ```java
    if (submission.getAssignment().getTeacher().getId() != teacherId) {
        throw new AccessDeniedException("Bạn không có quyền thêm nhận xét vào bài nộp này");
    }
    ```
