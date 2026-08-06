# Project Context — MathClass-service

## Commands
- Build: `./gradlew build`
- Test: `./gradlew test`
- Run local: `./gradlew bootRun`

## 1. Project Overview

- MathClass-service: Nền tảng bài tập toán THPT (Trung học Phổ thông) trực tuyến
- Mục đích: Xây dựng một hệ thống web cho phép giáo viên và học sinh THPT tương tác qua bài tập môn Toán một cách trực tuyến, liền mạch, giảm thiểu giấy tờ và nâng cao hiệu quả giảng dạy.
- Ưu tiên: code sạch, dễ bảo trì và mở rộng

## 2. Project Structure

- Package gốc: `com.codegym.mathclass`
- Cấu trúc dự án được tổ chức theo module/feature thay vì package-by-layer ở cấp cao nhất. Mỗi module (như `user`, `classroom`, `assignment`, `submission`) tự chứa các package con của riêng nó:
  - `<feature>/entity/` → JPA Entities (ví dụ: `User`, `Classroom`, `Assignment`, `Submission`)
  - `<feature>/repository/` → Spring Data JPA repositories
  - `<feature>/service/` + `service/impl/` → Interface và implementation business logic
  - `<feature>/controller/` → REST API endpoints
  - `<feature>/dto/` → Request/Response DTOs
- Thư mục dùng chung:
  - `common/entity/` → Chứa `BaseEntity` (id, createdAt, updatedAt)
  - `exception/` → Chứa `GlobalExceptionHandler` và các custom exceptions (`ResourceNotFoundException`, `BadRequestException`, `AccessDeniedException`)
- `application.properties` → Config PostgreSQL (mặc định)
- `postman/` → API collection để test

## 3. Coding Conventions & Standards

- Stack: Spring Boot 4.1.0, Java 21, Lombok, Spring Data JPA, Spring Security, Spring Web, Spring Validation, Spring JSON, Spring Mail, Spring Actuator
- Naming: camelCase cho field/method, PascalCase cho class
- DB: PostgreSQL, table names số nhiều (users, classrooms, assignments, submissions)
- Dùng @Builder, @Data, @NoArgsConstructor, @AllArgsConstructor từ Lombok
- Constructor injection (qua @RequiredArgsConstructor), không dùng @Autowired field injection
- Tất cả entities extend BaseEntity (id, createdAt, updatedAt)
- Tất cả classes phải sử dụng import ở đầu file

## 4. Architecture Patterns

- Pattern bắt buộc: Repository → Service → ServiceImpl → Controller   
- Không trả Entity trực tiếp ra Controller — luôn map qua DTO
- Trả về API response dạng trực tiếp DTO hoặc List DTO trong `ResponseEntity` thay vì bọc ngoài bằng class ApiResponse chung.
- Xử lý ngoại lệ tập trung qua `GlobalExceptionHandler` với cấu trúc response dạng Map có key `"error"`.

## 5. Response Style

- Trả về code Java hoàn chỉnh, copy paste được ngay
- Không thêm dependency ngoài build.gradle hiện tại
- Không tự ý sửa file config (application.properties...) trừ khi được yêu cầu trực tiếp
- Review code: dùng format có cấu trúc, không viết đoạn văn dài

## 6. Workflows & Modes

- Task phức tạp (feature mới, refactor nhiều file): đưa Implementation Plan trước, chờ xác nhận mới code
- Task đơn giản (fix 1 bug, sửa 1 method): code ngay không cần plan
- Trước khi sửa file: đọc qua @file để hiểu context hiện tại, không suy đoán nội dung
- Sau khi code xong: nhắc chạy ./gradlew build hoặc ./gradlew test để verify

## 7. Module-specific Rules

### 7.1. Authentication & Security Module (JWT)

- **Quy tắc Filter:** Tất cả các request (ngoại trừ các endpoint public như `/api/auth/**`) bắt buộc phải đi qua `AuthTokenFilter`. Không được tự ý disable filter này trong cấu hình Security.

- **Token Storage & Extraction:** Toàn bộ logic giải mã và trích xuất `UserDetails` từ JWT chỉ được thực hiện tại tầng Security (`AuthTokenFilter` và `JwtUtils`). Tuyệt đối không viết logic parse Token thủ công bên trong các Controller hay Service khác.
- **Context Access:** Khi cần lấy thông tin user hiện tại đang đăng nhập tại Controller, hãy ưu tiên dùng annotation `@AuthenticationPrincipal CustomUserDetails userDetails` trong param của controller method. Nếu cần lấy ở Service, sử dụng `SecurityContextHolder`.

### 7.2. Database & Data JPA Module

- **Quy tắc Entity:**
  - Tất cả các Class Entity phải sử dụng `@Table(name = "...")` để định nghĩa rõ tên bảng bằng tiếng Anh, chữ thường, số nhiều (ví dụ: `users`, `classrooms`).
  - Luôn sử dụng `@ManyToOne(fetch = FetchType.LAZY)` thay vì để mặc định (EAGER) để tránh lỗi n+1 query và tối ưu hiệu năng PostgreSQL.

- **Quy tắc Query:**
  - Ưu tiên sử dụng Derived Query Methods của Spring Data JPA. Nếu query phức tạp (Join nhiều bảng), bắt buộc phải dùng `@Query` viết JPQL hoặc Native Query.
  - Đối với các tác vụ `UPDATE` hoặc `DELETE` số lượng lớn qua JPA, bắt buộc phải có annotation `@Modifying` và `@Transactional`.

### 7.3. Validation & DTO Module

- **Quy tắc Input:** Tất cả các Request DTO nhận dữ liệu từ Client tại Controller phải được validate bằng annotation (`@NotNull`, `@NotBlank`, `@Size`, `@Email`,...).

- **Controller Trigger:** Tại tầng Controller, bắt buộc phải có annotation `@Valid` trước `@RequestBody` để kích hoạt bộ validate.
- **Custom Validator:** Các logic validate phức tạp liên quan đến database (ví dụ: kiểm tra trùng Email, trùng Username) phải được viết thành Custom Validator (implement `ConstraintValidator`) hoặc xử lý tập trung ở tầng Service.

### 7.4. Notification & Mail Module

- **Asynchronous Processing:** Tác vụ gửi Mail là tác vụ tốn thời gian. Do đó, phương thức gửi mail trong Service bắt buộc phải được đánh dấu bằng `@Async` để tránh block Main Thread. (Lưu ý: Phải có `@EnableAsync` trên class cấu hình của ứng dụng).

- **Template Management:** Không được hardcode nội dung HTML của Email trong code Java. Phải sử dụng cấu hình template ngoài hoặc Template Engine để quản lý nội dung mail.

### 7.5. Actuator & Monitoring Module

- **Health Check:** Endpoint `/actuator/health` phải luôn ở trạng thái public để các hệ thống Monitor (hoặc Docker/Kubernetes) có thể kiểm tra tình trạng ứng dụng. (Cần cấu hình cho phép truy cập public trong `SecurityConfig` nếu chưa cấu hình).

## 8. Session Management

Cuối mỗi session, tự động tạo summary với format:

- Đang làm gì?
- Đã làm xong gì?
- Decision đã chốt?
- Task tiếp theo là gì?

## 9. Forbidden File

- Không được phép đọc và chỉnh sửa các file sau: application-local.properties, .env

## 10. Quy tắc Cấu trúc Code Java

### Quản lý Import và Khai báo Class

- **LUÔN LUÔN** sử dụng câu lệnh `import` tường minh ở đầu file cho tất cả các class, entity, DTO, hoặc utility từ package khác.
- **TUYỆT ĐỐI KHÔNG** viết trực tiếp đường dẫn đầy đủ của package (fully qualified name - FQN) trong thân code (ví dụ: KHÔNG viết `org.example.user.entity.User user = ...`).
- **CÁCH LÀM ĐÚNG:**

  ```java
  import org.example.user.entity.User;
  
  // Trong thân class
  User user = new User();

## Reference Example (Controller + Service pattern)

Controller mỏng, không chứa business logic, chỉ nhận request → gọi Service → trả DTO:

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.authenticateUser(loginRequest));
    }
}


Service chứa business logic, tự validate điều kiện nghiệp vụ và throw custom exception (không trả lỗi qua return null/boolean):


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    @Override
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                throw new BadRequestException("Bạn chưa thiết lập mật khẩu...");
            }
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return new JwtResponse(jwt, userDetails.getId(), userDetails.getEmail(),
                userDetails.getFullName(), /* role */ "", userDetails.getAvatarUrl());
    }
}

