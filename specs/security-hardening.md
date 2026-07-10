# Spec: Cập nhật và Khắc phục Lỗ hổng Bảo mật (Security Hardening)

## Objective
Khắc phục triệt để các lỗi cấu hình và lỗ hổng kiểm soát truy cập (BOLA/IDOR) đã được phát hiện trong tài liệu [security-review.md](../reviews/security-review.md), nhằm đảm bảo:
1. Hệ thống hoạt động ổn định không bị crash khi cấu hình JWT Secret.
2. Học sinh không thể xem hoặc chỉnh sửa bài tập, bài nộp, hình vẽ, hoặc nhận xét của học sinh khác (Ngăn chặn IDOR/BOLA).
3. Làm sạch mọi dữ liệu đầu vào chứa mã LaTeX nguy hiểm từ cả giáo viên và học sinh.

---

## Tech Stack
*   Không thay đổi (Java 21, Spring Boot Security, PostgreSQL).

---

## Commands
*   Chạy kiểm thử bảo mật và hồi quy: `./gradlew test`
*   Build dự án: `./gradlew build`
*   Chạy backend cục bộ: `./gradlew bootRun`

---

## Project Structure & Proposed Changes

Các thay đổi sẽ được áp dụng trực tiếp lên các tệp nguồn sau:

### 1. Cấu hình JWT Secret
#### [MODIFY] [application.properties](../src/main/resources/application.properties)
*   Bơm biến môi trường từ hệ thống/dotenv vào cấu hình Spring:
    `mathclass.app.jwtSecret=${JWT_SECRET}`

#### [MODIFY] [.env.example](../.env.example)
*   Thêm khóa môi trường mặc định có giá trị Base64 hợp lệ (tối thiểu 512 bits / 64 bytes ngẫu nhiên mã hóa Base64) để làm hướng dẫn cho môi trường phát triển:
    `JWT_SECRET=dGhpc19pc19hX3NlY3VyZV9hbmRfZ2VuZXJhdGVkX2Jhc2U2NF9rZXlfZm9yX21hdGhj bGFzc19hcHBsaWNhdGlvbl81MTJiaXRz`

#### [MODIFY] [JwtUtils.java](../src/main/java/com/codegym/mathclass/security/jwt/JwtUtils.java)
*   Thay đổi giá trị mặc định của thuộc tính `@Value("${mathclass.app.jwtSecret}")` thành chuỗi Base64 hợp lệ, phòng trường hợp file `.env` bị thiếu:
    `@Value("${mathclass.app.jwtSecret:dGhpc19pc19hX3NlY3VyZV9hbmRfZ2VuZXJhdGVkX2Jhc2U2NF9rZXlfZm9yX21hdGhj bGFzc19hcHBsaWNhdGlvbl81MTJiaXRz}")`

---

### 2. Kiểm soát Truy cập Hình vẽ Bài nộp (BOLA)
#### [MODIFY] [SubmissionDrawingServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/SubmissionDrawingServiceImpl.java)
*   Trong phương thức `getDrawingBySubmissionId(long submissionId, String currentUserUsername)`, lấy thông tin User hiện tại từ email để xác thực:
    *   Nếu User có vai trò `STUDENT`: Email của học sinh nộp bài phải trùng khớp với email người dùng hiện tại (`submission.getStudent().getEmail().equals(currentUserUsername)`).
    *   Nếu User có vai trò `TEACHER`: Giáo viên phụ trách bài tập của bài nộp này phải trùng khớp với người dùng hiện tại (`submission.getAssignment().getTeacher().getEmail().equals(currentUserUsername)`).
    *   Nếu không thỏa mãn bất kỳ điều kiện nào, ném ra lỗi `AccessDeniedException`.

---

### 3. Kiểm soát Truy cập Bình luận Bài nộp (BOLA)
#### [MODIFY] [SubmissionCommentService.java](../src/main/java/com/codegym/mathclass/submission/service/SubmissionCommentService.java)
*   Cập nhật chữ ký hàm `getCommentsBySubmissionId` và `addComment` để nhận thêm thông tin ID và vai trò của người dùng hiện tại:
    ```java
    List<SubmissionCommentResponse> getCommentsBySubmissionId(Long submissionId, Long currentUserId, String userRole);
    SubmissionCommentResponse addComment(Long submissionId, Long teacherId, SubmissionCommentRequest request);
    ```

#### [MODIFY] [SubmissionCommentServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/impl/SubmissionCommentServiceImpl.java)
*   Trong `getCommentsBySubmissionId(Long submissionId, Long currentUserId, String userRole)`:
    *   Nếu là học sinh (`Role.STUDENT`): Kiểm tra `submission.getStudent().getId() == currentUserId`.
    *   Nếu là giáo viên (`Role.TEACHER`): Kiểm tra `submission.getAssignment().getTeacher().getId() == currentUserId`.
    *   Nếu sai quyền truy cập, ném ra lỗi `AccessDeniedException`.
*   Trong `addComment(Long submissionId, Long teacherId, SubmissionCommentRequest request)`:
    *   Xác minh giáo viên bình luận phải chính là giáo viên đã giao bài tập này (`submission.getAssignment().getTeacher().getId() == teacherId`). Nếu sai, ném ra `AccessDeniedException`.

#### [MODIFY] [SubmissionCommentController.java](../src/main/java/com/codegym/mathclass/submission/controller/SubmissionCommentController.java)
*   Cập nhật `getCommentsBySubmissionId` để truyền thông tin `userDetails.getId()` và vai trò của người dùng vào Service:
    ```java
    String userRole = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst()
            .orElse("");
    ```

---

### 4. Kiểm soát Truy cập Xem Bài tập của Học sinh (BOLA)
#### [MODIFY] [AssignmentServiceImpl.java](../src/main/java/com/codegym/mathclass/assignment/service/impl/AssignmentServiceImpl.java)
*   Trong phương thức `getAssignmentById(long assignmentId, long userId, String role)`:
    *   Nếu người gọi là `STUDENT`: Bổ sung kiểm tra xem học sinh đó có thuộc danh sách học sinh của lớp học được giao bài tập này hay không:
        ```java
        boolean isEnrolled = assignment.getClassroom().getStudents().stream()
                .anyMatch(s -> s.getId() == userId);
        if (!isEnrolled) {
            throw new AccessDeniedException("Bạn không có quyền truy cập bài tập của lớp học này");
        }
        ```

---

### 5. Làm sạch LaTeX nguy hiểm trong Bài nộp và Bình luận (Input Validation)
#### [MODIFY] [SubmissionServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/SubmissionServiceImpl.java)
*   Trong phương thức `createSubmission` và `updateSubmission`: Tiến hành validate thuộc tính `content` của `SubmissionRequest` bằng `LaTeXSanitizer.isSafe()`.
*   Trong phương thức `gradeSubmission`: Tiến hành validate thuộc tính `teacherFeedback` của `GradeRequest` bằng `LaTeXSanitizer.isSafe()`.
*   Nếu phát hiện lệnh nguy hiểm, ném ra lỗi `BadRequestException` kèm tên lệnh nguy hiểm.

#### [MODIFY] [SubmissionCommentServiceImpl.java](../src/main/java/com/codegym/mathclass/submission/service/impl/SubmissionCommentServiceImpl.java)
*   Trong phương thức `addComment`: Validate thuộc tính `content` trong `SubmissionCommentRequest` bằng `LaTeXSanitizer.isSafe()`. Nếu phát hiện mã độc hại, ném ra lỗi `BadRequestException`.

---

## Code Style
*   Sử dụng cơ chế Exception Handler tập trung của dự án:
    *   Ném ra `AccessDeniedException` để trả về HTTP status 403 Forbidden.
    *   Ném ra `BadRequestException` để trả về HTTP status 400 Bad Request.

---

## Testing Strategy
Bổ sung các test case giả lập hành vi xâm nhập trái phép (Abuse Cases):

### 1. Đăng nhập & Xác thực JWT
*   Chạy test kiểm tra tính hợp lệ của việc đọc và giải mã khóa JWT Secret ngẫu nhiên mới từ file cấu hình.

### 2. Kiểm thử BOLA/IDOR
*   **Test Case 1:** Student A đăng nhập và cố gắng gọi API lấy hình vẽ của Student B -> Kỳ vọng nhận phản hồi HTTP 403 Forbidden.
*   **Test Case 2:** Student A đăng nhập và cố gắng gọi API lấy bình luận bài làm của Student B -> Kỳ vọng nhận phản hồi HTTP 403 Forbidden.
*   **Test Case 3:** Student A cố gắng lấy thông tin bài tập được giao cho lớp của Student B (lớp mà Student A không tham gia) -> Kỳ vọng nhận phản hồi HTTP 403 Forbidden.
*   **Test Case 4:** Teacher A cố gắng thêm bình luận hoặc lấy chi tiết bài nộp của một bài tập do Teacher B phụ trách -> Kỳ vọng nhận phản hồi HTTP 403 Forbidden.

### 3. Kiểm thử LaTeX Injection
*   **Test Case 5:** Student nộp bài có nội dung chứa `\input{/etc/passwd}` -> Kỳ vọng API nộp bài thất bại và trả về HTTP 400 Bad Request.
*   **Test Case 6:** Giáo viên phản hồi bài làm có chứa `\write18{...}` -> Kỳ vọng API chấm điểm thất bại và trả về HTTP 400 Bad Request.

---

## Success Criteria
*   [ ] Toàn bộ API hoạt động mà không bị crash khi cấu hình JWT Secret hợp lệ.
*   [ ] Trả về mã lỗi 403 Forbidden đối với tất cả các kịch bản thử nghiệm IDOR/BOLA ở trên.
*   [ ] Trả về mã lỗi 400 Bad Request kèm thông điệp báo lỗi rõ ràng khi người dùng gửi mã LaTeX độc hại.
*   [ ] Toàn bộ các test case bảo mật mới viết đều chạy qua (PASS).
*   [ ] Không ảnh hưởng đến các tính năng hiện tại của dự án.
