# Backend AI Agent Guidelines (MathClass-service)

Tập tin này định nghĩa quy tắc hoạt động, thứ tự nạp ngữ cảnh và quy chuẩn phát triển dành cho AI Agent khi làm việc trong dự án **MathClass-service**.

---

## 1. Context Loading Order (Thứ tự nạp Ngữ cảnh)

Trước khi thực hiện bất kỳ nhiệm vụ nào (phát triển tính năng, sửa bug, refactor), AI **BẮT BUỘC** phải nạp và tuân thủ ngữ cảnh theo thứ tự sau:

1. 📖 **Tổng quan dự án & Nghiệp vụ:** Đọc [README.md](../README.md) để nắm bức tranh tổng thể dự án.
2. 🔴 **Quy chuẩn kỹ thuật BẮT BUỘC:** Đọc [.antigravity/rules.md](../.antigravity/rules.md) chứa toàn bộ Coding Conventions, Naming Standards và Architecture Patterns.
3. 🐳 **Môi trường & Vận hành:** Tham khảo [05-docker-guide.md](../docs/05-docker-guide.md) khi cần làm việc với Docker/PostgreSQL.

---

## 2. Tech Stack & Môi trường Phát triển

Chi tiết danh sách Tech Stack và thư viện được quản lý tập trung tại [README.md](../README.md) và [.antigravity/rules.md](../.antigravity/rules.md).

- **Lưu ý nhanh về Môi trường (Operational Snapshot):**
  - **Build Tool:** Gradle Wrapper (`./gradlew`)
  - **Database Host Port:** `5433` (PostgreSQL Container port: `5423` / `5432`)

---

## 3. Skill Trigger Rules (Tự Động Kích Hoạt Skill)

AI cần tự động áp dụng các skill sau theo đúng loại tác vụ:

- **Khi thiết kế, tạo mới hoặc refactor REST API:** ➔ Sử dụng skill [api-design-principles](skills/api-design-principles/SKILL.md)
- **Khi thảo luận, làm rõ ý tưởng, kiến trúc hoặc nghiệp vụ mới trước khi code:** ➔ Sử dụng skill [brainstorming](skills/brainstorming/SKILL.md)
- **Khi Refactor, tối ưu hóa code, hoặc sửa code chưa sạch:** ➔ Sử dụng skill [clean-code](skills/clean-code/SKILL.md)
- **Khi đánh giá, review code hoặc kiểm tra chất lượng Pull Request:** ➔ Sử dụng skill [code-reviewer](skills/code-reviewer/SKILL.md)
- **Khi làm việc với Docker, Dockerfile, Docker Compose:** ➔ Sử dụng skill [docker-expert](skills/docker-expert/SKILL.md)
- **Khi làm việc với Java 21, Spring Boot 4.x hoặc các tính năng Java hiện đại:** ➔ Sử dụng skill [java-pro](skills/java-pro/SKILL.md)
- **Khi tạo/sửa JPA Entity, Repository, Query HQL/SQL:** ➔ Sử dụng skill [spring-data-jpa](skills/spring-data-jpa/SKILL.md)
- **Khi áp dụng chuẩn kiến trúc, thiết kế hệ thống theo phong cách Uncle Bob (Clean Architecture, SOLID):** ➔ Sử dụng skill [uncle-bob-craft](skills/uncle-bob-craft/SKILL.md)
- **Khi viết Unit Test / Integration Test:** ➔ Sử dụng skill [unit-testing-test-generate](skills/unit-testing-test-generate/SKILL.md) hoặc [java-pro](skills/java-pro/SKILL.md)

---

## 4. Nguyên Tắc Kiến Trúc & Coding Standards

- **Kiến trúc phân tầng (Package by Feature):**
  - `controller`: Chỉ nhận HTTP Request, validate DTO bằng `@Valid`, gọi Service, trả về `ResponseEntity<ApiResponse<T>>`. **Không viết logic tại Controller**.
  - `service` / `service/impl`: Chứa toàn bộ Business Logic. Sử dụng `@Transactional` cho các hàm tác động dữ liệu.
  - `repository`: Interfaces kế thừa `JpaRepository` / `JpaSpecificationExecutor`.
  - `dto`: Phân tách `RequestDTO` và `ResponseDTO`. **Không trả về JPA Entity trực tiếp ra API Response**.
  - `exception`: Bắt ngoại lệ tập trung qua `GlobalExceptionHandler`.
- **Dependency Injection:** Sử dụng Constructor Injection thông qua `@RequiredArgsConstructor` từ Lombok (KHÔNG dùng `@Autowired` ở trường).
- **Entities:** Tất cả JPA Entities phải kế thừa từ `BaseEntity` (chứa `id`, `createdAt`, `updatedAt`).

---

## 5. Verification Checklist (Kiểm tra bắt buộc)

Sau khi chỉnh sửa code, AI **BẮT BUỘC** phải tự động chạy kiểm tra để đảm bảo ứng dụng biên dịch thành công trước khi hoàn tất:

```bash
# Kiểm tra biên dịch Java
./gradlew compileJava

# Chạy Unit Tests (nếu có)
./gradlew test
```

---

## 6. Git Branch & Commit Conventions (Quy chuẩn Git & Commit)

Khi người dùng yêu cầu AI tạo nhánh, tạo commit hoặc push code lên GitHub, AI **BẮT BUỘC** phải tuân thủ các quy tắc sau:

> 💡 **Ghi chú về `<mã-task-jira>`:**
>
> - **Tên nhánh:** Ưu tiên sử dụng mã **Main Task / Story / Bug ID** (để quản lý theo tính năng hoặc lỗi tổng thể).
> - **Commit Message:** Ưu tiên sử dụng mã **Sub-task / Sub-bug ID** (nếu task/bug được chia nhỏ thành Sub-task trên Jira), hoặc mã **Main Task / Bug ID** (nếu làm việc trực tiếp trên Ticket chính).

### 🌿 Quy tắc đặt tên nhánh (Branch Naming)

Cấu trúc bắt buộc: `<type>/<mã-task-jira>/<tên-tính-năng>` (tên tính năng dùng `kebab-case`).

- `feature/<mã-task-jira>/<tên-tính-năng>` : Phát triển tính năng mới (ví dụ: `feature/MAT-101/user-avatar-upload`)
- `bugfix/<mã-task-jira>/<tên-lỗi>` : Sửa lỗi / Bugfix (ví dụ: `fix/MAT-205/jwt-expiration-error`)
- `refactor/<mã-task-jira>/<tên-mô-tả>` : Tối ưu hóa, cấu trúc lại code (ví dụ: `refactor/MAT-302/submission-service`)
- `test/<mã-task-jira>/<tên-mô-tả>` : Bổ sung kiểm thử / test suite (ví dụ: `test/MAT-401/classroom-integration`)
- `chore/<mã-task-jira>/<tên-mô-tả>` : Cấu hình dependencies, Docker, CI/CD (ví dụ: `chore/MAT-500/update-gradle`)

### 💬 Quy tắc Commit Message (Conventional Commits)

Cấu trúc: `<type>(<mã-task-jira>): <nội dung mô tả ngắn gọn>`

- `feat(MAT-101): bổ sung API upload ảnh đại diện cá nhân`
- `fix(MAT-205): sửa lỗi hết hạn token JWT khi gọi API`
- `refactor(MAT-302): tối ưu hóa query JPA lấy danh sách học sinh`
- `test(MAT-401): bổ sung Unit Test và Integration Test cho module Classroom`
- `docs(MAT-500): cập nhật quy chuẩn Git Branch và Commit vào AGENTS.md`
- `chore(MAT-600): cập nhật cấu hình build Gradle và Dockerfile`
