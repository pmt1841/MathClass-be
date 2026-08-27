package com.codegym.mathclass.config;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentDrawingRepository;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.entity.ClassroomJoinRequest;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.notification.entity.Notification;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationRepository;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionComment;
import com.codegym.mathclass.submission.entity.SubmissionDrawing;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionCommentRepository;
import com.codegym.mathclass.submission.repository.SubmissionDrawingRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.config.DefaultRolePermissions;
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.entity.Permission;
import com.codegym.mathclass.user.entity.RolePermission;
import com.codegym.mathclass.user.repository.PermissionRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.entity.AiCreditDefault;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditConfigRepository;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditDefaultRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.entity.SystemPromptHistory;
import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import com.codegym.mathclass.aiconfig.repository.SystemPromptHistoryRepository;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

        private final UserRepository userRepository;
        private final ClassroomRepository classroomRepository;
        private final ClassroomJoinRequestRepository classroomJoinRequestRepository;
        private final AssignmentRepository assignmentRepository;
        private final AssignmentDrawingRepository assignmentDrawingRepository;
        private final SubmissionRepository submissionRepository;
        private final SubmissionCommentRepository submissionCommentRepository;
        private final SubmissionDrawingRepository submissionDrawingRepository;
        private final NotificationRepository notificationRepository;
        private final NotificationSettingsRepository notificationSettingsRepository;
        private final PasswordEncoder passwordEncoder;
        private final PermissionRepository permissionRepository;
        private final RolePermissionRepository rolePermissionRepository;
        private final AiCreditService aiCreditService;
        private final AiCreditDefaultRepository aiCreditDefaultRepository;
        private final AiCreditConfigRepository aiCreditConfigRepository;
        private final CreditPackageRepository creditPackageRepository;
        private final SystemPromptRepository systemPromptRepository;
        private final SystemPromptHistoryRepository systemPromptHistoryRepository;

        @Value("${mathclass.seed.enabled:true}")
        private boolean isSeedEnabled;

        @Override
        @Transactional
        public void run(String... args) throws Exception {
                if (!isSeedEnabled) {
                        log.info("[DatabaseSeeder] Database seeding is disabled.");
                        return;
                }

                // Always ensure permissions and role permissions are up to date (idempotent)
                seedPermissions();

                seedAiCreditData();

                // Always ensure system prompts are synchronized and up to date (idempotent)
                seedSystemPrompts();

                if (userRepository.count() > 0) {
                        log.info("[DatabaseSeeder] Users exist in database. Skipping sample data seeding.");
                        return;
                }

                log.info("[DatabaseSeeder] Database is empty. Seeding sample data...");

                try {
                        seedData();
                        aiCreditService.backfillExistingUsers();
                        log.info("[DatabaseSeeder] Database seeded successfully.");
                } catch (Exception e) {
                        log.error("[DatabaseSeeder] Error during database seeding!", e);
                        throw e;
                }
        }

        private void seedPermissions() {
                log.info("[DatabaseSeeder] Synchronizing permissions and role permissions...");

                List<Permission> requiredPermissions = List.of(
                                p("classroom:create", "Tạo lớp học"),
                                p("classroom:update", "Sửa lớp học"),
                                p("classroom:delete", "Xóa lớp học"),
                                p("classroom:manage_requests", "Quản lý yêu cầu tham gia"),
                                p("classroom:remove_student", "Xóa học sinh"),
                                p("classroom:join", "Tham gia lớp"),
                                p("classroom:join_status", "Xem trạng thái tham gia"),

                                p("assignment:create", "Tạo bài tập"),
                                p("assignment:update", "Sửa bài tập"),
                                p("assignment:delete", "Xóa bài tập"),
                                p("assignment:publish", "Xuất bản bài tập"),
                                p("assignment:read", "Xem bài tập"),

                                p("submission:submit", "Nộp bài"),
                                p("submission:read_own", "Xem bài nộp của mình"),
                                p("submission:grade", "Chấm điểm"),
                                p("submission:read_all", "Xem tất cả bài nộp"),
                                p("submission:comment", "Bình luận bài nộp"),

                                p("dashboard:teacher_view", "Xem thống kê giáo viên"),
                                p("dashboard:student_view", "Xem thống kê học sinh"),

                                p("library:read", "Xem thư viện bài tập dùng chung"),
                                p("library:clone", "Clone bài tập từ thư viện"),

                                p("user:manage", "Quản lý người dùng"));

                Map<String, Permission> existingPermMap = permissionRepository.findAll().stream()
                                .collect(Collectors.toMap(Permission::getName, p -> p));

                List<Permission> newPermissionsToSave = requiredPermissions.stream()
                                .filter(p -> !existingPermMap.containsKey(p.getName()))
                                .toList();

                if (!newPermissionsToSave.isEmpty()) {
                        List<Permission> savedNew = permissionRepository.saveAll(newPermissionsToSave);
                        savedNew.forEach(p -> existingPermMap.put(p.getName(), p));
                        log.info("[DatabaseSeeder] Added {} new permissions via batch insert.", savedNew.size());
                }

                Set<String> existingRolePermKeys = rolePermissionRepository.findAll().stream()
                                .map(rp -> rp.getRole().name() + ":" + rp.getPermission().getName())
                                .collect(Collectors.toSet());

                List<RolePermission> newRolePermissions = new ArrayList<>();
                for (Role role : Role.values()) {
                        List<String> defaultPermNames = DefaultRolePermissions.getDefaultPermissions(role);
                        for (String permName : defaultPermNames) {
                                String key = role.name() + ":" + permName;
                                Permission p = existingPermMap.get(permName);
                                if (p != null && !existingRolePermKeys.contains(key)) {
                                        newRolePermissions
                                                        .add(RolePermission.builder().role(role).permission(p).build());
                                }
                        }
                }

                if (!newRolePermissions.isEmpty()) {
                        rolePermissionRepository.saveAll(newRolePermissions);
                        log.info("[DatabaseSeeder] Assigned {} new role-permission mappings via batch insert.",
                                        newRolePermissions.size());
                }
        }

        private void seedAiCreditData() {
                seedCreditDefaults();
                seedCreditConfigs();
                seedCreditPackages();
                // Backfill cho các user đã tồn tại trước khi deploy tính năng credit
                // (idempotent)
                aiCreditService.backfillExistingUsers();
        }

        private void seedCreditDefaults() {
                for (Role role : List.of(Role.STUDENT, Role.TEACHER)) {
                        if (aiCreditDefaultRepository.findByRole(role).isEmpty()) {
                                int defaultCredits = role == Role.TEACHER ? 500 : 100;
                                aiCreditDefaultRepository.save(AiCreditDefault.builder()
                                                .role(role)
                                                .defaultCredits(defaultCredits)
                                                .build());
                                log.info("[DatabaseSeeder] Seeded default credits for role {} = {}.", role,
                                                defaultCredits);
                        }
                }
        }

        private void seedCreditConfigs() {
                int defaultTokensPerCredit = 1000;
                Map<String, Integer> defaults = Map.of(
                                "STUDENT_HINT", 1,
                                "CANVAS_LATEX", 2,
                                "QUESTION_GEN", 3,
                                "BATCH_QUESTION_GEN", 2,
                                "SUBMISSION_GRADING", 5);
                defaults.forEach((task, cost) -> {
                        AiCreditConfig existing = aiCreditConfigRepository.findByTask(task).orElse(null);
                        if (existing == null) {
                                aiCreditConfigRepository.save(AiCreditConfig.builder()
                                                .task(task)
                                                .costPerCall(cost)
                                                .tokensPerCredit(defaultTokensPerCredit)
                                                .enabled(true)
                                                .build());
                                log.info("[DatabaseSeeder] Seeded credit config for task {} = {} credit.", task, cost);
                        } else if (existing.getTokensPerCredit() == null) {
                                existing.setTokensPerCredit(defaultTokensPerCredit);
                                aiCreditConfigRepository.save(existing);
                                log.info("[DatabaseSeeder] Backfilled tokensPerCredit={} for task {}.",
                                                defaultTokensPerCredit, task);
                        }
                });
        }

        private void seedCreditPackages() {
                if (creditPackageRepository.count() == 0) {
                        creditPackageRepository.saveAll(List.of(
                                        CreditPackage.builder().name("Gói Cơ bản").credits(100).price(20000)
                                                        .enabled(true).sortOrder(1).build(),
                                        CreditPackage.builder().name("Gói Pro").credits(300).price(50000).enabled(true)
                                                        .sortOrder(2).build(),
                                        CreditPackage.builder().name("Gói VIP").credits(1000).price(150000)
                                                        .enabled(true).sortOrder(3).build()));
                        log.info("[DatabaseSeeder] Seeded 3 default credit packages.");
                }
        }

        private void seedData() {
                // 1. Create Users (Admin, Teachers, Students)
                log.info("[DatabaseSeeder] Creating sample users...");
                User admin = createUser("admin@mathclass.com", "Admin Hệ Thống", "password123", "0901234567",
                                Role.ADMIN,
                                Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=admin");
                User teacher1 = createUser("teacher1@mathclass.com", "Thầy Nguyễn Văn A", "password123", "0902234567",
                                Role.TEACHER, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=teacher1");
                User teacher2 = createUser("teacher2@mathclass.com", "Cô Trần Thị B", "password123", "0903234567",
                                Role.TEACHER,
                                Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=teacher2");

                User student1 = createUser("student1@mathclass.com", "Học sinh Nguyễn Văn An", "password123",
                                "0904234567",
                                Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student1");
                User student2 = createUser("student2@mathclass.com", "Học sinh Lê Thị Bình", "password123",
                                "0905234567",
                                Role.STUDENT, Gender.FEMALE,
                                "https://api.dicebear.com/7.x/adventurer/svg?seed=student2");
                User student3 = createUser("student3@mathclass.com", "Học sinh Phạm Văn Cường", "password123",
                                "0906234567",
                                Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student3");
                User student4 = createUser("student4@mathclass.com", "Học sinh Hoàng Thị Dung", "password123",
                                "0907234567",
                                Role.STUDENT, Gender.FEMALE,
                                "https://api.dicebear.com/7.x/adventurer/svg?seed=student4");
                User student5 = createUser("student5@mathclass.com", "Học sinh Vũ Văn Em", "password123", "0908234567",
                                Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student5");

                // 2. Create Classrooms
                log.info("[DatabaseSeeder] Creating classrooms...");
                Classroom class1 = classroomRepository.save(Classroom.builder()
                                .classCode("MATH101")
                                .className("Lớp Toán Đại số 10")
                                .teacher(teacher1)
                                .maxStudents(30)
                                .description("Lớp toán đại số cơ bản dành cho học sinh lớp 10 năm học 2026-2027.")
                                .students(new HashSet<>(Set.of(student1, student2, student3)))
                                .build());

                Classroom class2 = classroomRepository.save(Classroom.builder()
                                .classCode("MATH102")
                                .className("Lớp Toán Hình học 11")
                                .teacher(teacher2)
                                .maxStudents(25)
                                .description("Lớp học chuyên đề hình học không gian và phương pháp tọa độ lớp 11.")
                                .students(new HashSet<>(Set.of(student3, student4, student5)))
                                .build());

                // 3. Create Classroom Join Requests
                log.info("[DatabaseSeeder] Creating join requests...");
                classroomJoinRequestRepository.save(ClassroomJoinRequest.builder()
                                .classroom(class1)
                                .student(student4)
                                .status(JoinRequestStatus.PENDING)
                                .build());

                classroomJoinRequestRepository.save(ClassroomJoinRequest.builder()
                                .classroom(class1)
                                .student(student5)
                                .status(JoinRequestStatus.REJECTED)
                                .build());

                // 4. Create Assignments
                log.info("[DatabaseSeeder] Creating assignments...");
                Assignment assign1 = assignmentRepository.save(Assignment.builder()
                                .title("Bài tập hàm số bậc hai")
                                .description("Các em hoàn thành các bài tập sau về hàm số bậc hai $y = ax^2 + bx + c$. Yêu cầu vẽ đồ thị phụ họa.")
                                .content("1. Khảo sát sự biến thiên và vẽ đồ thị hàm số $y = x^2 - 4x + 3$.\n2. Tìm m để phương trình $x^2 - 2(m+1)x + m^2 + 2 = 0$ có hai nghiệm phân biệt.")
                                .teacher(teacher1)
                                .classroom(class1)
                                .status(AssignmentStatus.PUBLISHED)
                                .deadline(LocalDateTime.now().plusDays(7))
                                .build());

                // Add drawing to Assignment 1
                Map<String, Object> graphData = Map.of(
                                "type", "parabola",
                                "a", 1.0,
                                "b", -4.0,
                                "c", 3.0);

                assignmentDrawingRepository.save(AssignmentDrawing.builder()
                                .assignment(assign1)
                                .shapeCode("PARABOLA_01")
                                .jsxGraphData(graphData)
                                .build());

                Assignment assign2 = assignmentRepository.save(Assignment.builder()
                                .title("Bài tập vectơ trong không gian")
                                .description("Học sinh thực hiện vẽ hình biểu diễn và tính tích vô hướng của hai vectơ $\\vec{u}$ và $\\vec{v}$ trong không gian Oxyz.")
                                .content("Cho hình chóp S.ABCD có đáy ABCD là hình vuông cạnh a. Cạnh bên SA vuông góc với mặt phẳng đáy và SA = a. Tính góc giữa đường thẳng SD và mặt phẳng (SBC).")
                                .teacher(teacher2)
                                .classroom(class2)
                                .status(AssignmentStatus.PUBLISHED)
                                .deadline(LocalDateTime.now().plusDays(5))
                                .build());

                Assignment assign3 = assignmentRepository.save(Assignment.builder()
                                .title("Bài tập trắc nghiệm số học (DRAFT)")
                                .description("Bài tập kiểm tra kiến thức về ước chung lớn nhất và bội chung nhỏ nhất.")
                                .content("Câu 1: Tìm UCLN(24, 36).\nCâu 2: Số nguyên tố là gì?")
                                .teacher(teacher1)
                                .status(AssignmentStatus.DRAFT)
                                .deadline(null)
                                .build());

                // 5. Create Submissions
                log.info("[DatabaseSeeder] Creating submissions...");
                // Submission 1: Graded
                Submission sub1 = submissionRepository.save(Submission.builder()
                                .assignment(assign1)
                                .student(student1)
                                .content("Em xin gửi bài làm hàm số bậc hai: \n1. Hàm số $y = x^2 - 4x + 3$ có tọa độ đỉnh $I(2, -1)$, cắt Oy tại $(0, 3)$, cắt Ox tại $(1, 0)$ và $(3, 0)$.")
                                .status(SubmissionStatus.GRADED)
                                .score(9.0)
                                .submittedAt(LocalDateTime.now().minusDays(1))
                                .teacherFeedback("Bài làm tốt, trình bày sạch sẽ và giải toán chính xác.")
                                .build());

                // Add Submission Comment
                SubmissionComment comment1 = SubmissionComment.builder()
                                .submission(sub1)
                                .teacher(teacher1)
                                .quoteText("tọa độ đỉnh I(2, -1)")
                                .occurrenceIndex(0)
                                .content("Đúng rồi, đỉnh I có tọa độ chính xác.")
                                .build();
                submissionCommentRepository.save(comment1);

                // Add Submission Drawing
                Map<String, Object> drawingData = Map.of(
                                "points", List.of(Map.of("name", "I", "x", 2.0, "y", -1.0)));
                Map<String, Object> drawingMeta = Map.of(
                                "tool", "JSXGraph",
                                "version", "1.4.2");

                SubmissionDrawing subDraw1 = SubmissionDrawing.builder()
                                .submission(sub1)
                                .shapeCode("STUDENT_PARABOLA_01")
                                .jsxGraphData(drawingData)
                                .metadata(drawingMeta)
                                .build();
                submissionDrawingRepository.save(subDraw1);

                // Submission 2: Submitted (Ungraded)
                Submission sub2 = submissionRepository.save(Submission.builder()
                                .assignment(assign1)
                                .student(student2)
                                .content("Bài làm của em Lê Thị Bình: \nCâu 1: Hàm số có bảng biến thiên đi xuống từ $-\\infty$ đến $2$ và đi lên từ $2$ đến $+\\infty$.")
                                .status(SubmissionStatus.SUBMITTED)
                                .submittedAt(LocalDateTime.now().minusHours(2))
                                .build());

                // Submission 3: Graded
                Submission sub3 = submissionRepository.save(Submission.builder()
                                .assignment(assign2)
                                .student(student3)
                                .content("Lời giải hình học không gian S.ABCD: \nGóc giữa SD và (SBC) bằng góc $\\widehat{DSE}$ với E là hình chiếu của D lên SB...")
                                .status(SubmissionStatus.GRADED)
                                .score(8.5)
                                .submittedAt(LocalDateTime.now().minusDays(2))
                                .teacherFeedback("Phân tích góc tốt, tính toán chính xác.")
                                .build());

                // 6. Create Notifications
                log.info("[DatabaseSeeder] Creating notifications...");
                Notification notif1 = Notification.builder()
                                .user(student1)
                                .message("Bài tập 'Bài tập hàm số bậc hai' của bạn đã được chấm điểm: 9.0")
                                .link("/assignments/" + assign1.getId() + "?classCode="
                                                + assign1.getClassroom().getClassCode())
                                .isRead(false)
                                .build();
                notificationRepository.save(notif1);

                Notification notif2 = Notification.builder()
                                .user(teacher1)
                                .message("Học sinh Lê Thị Bình đã nộp bài tập 'Bài tập hàm số bậc hai'")
                                .link("/assignments/" + assign1.getId() + "/submissions/" + sub2.getId())
                                .isRead(false)
                                .build();
                notificationRepository.save(notif2);
        }

        private void seedSystemPrompts() {
                log.info("[DatabaseSeeder] Initializing or updating system prompts...");

                String defaultHintPrompt = """
                                Bạn là một trợ lý giáo viên môn {{subject}} xuất sắc và kiên nhẫn.
                                Nhiệm vụ của bạn là đưa ra 01 GỢI Ý TƯ DUY NGẮN (từ 50 đến 120 từ) định hướng BƯỚC TIẾP THEO bám sát chính xác bài toán dưới đây.

                                [ĐỀ BÀI CẦN GIẢI]:
                                - Tiêu đề: {{title}}
                                - Nội dung đề bài: {{problem_content}}

                                [TIẾN ĐỘ BÀI LÀM HIỆN TẠI CỦA HỌC SINH]:
                                {{student_content}}

                                YÊU CẦU NỘI DUNG BẮT BUỘC:
                                1. Đọc và phân tích kỹ bài toán cụ thể nêu ở trên.
                                2. Phân tích tiến độ bài làm của học sinh:
                                   - Nếu bài làm trống: hãy chỉ rõ giả thiết, phương pháp hoặc công thức đầu tiên học sinh cần áp dụng để bắt đầu.
                                   - Nếu học sinh đã viết bài làm: hãy nhận xét ngắn gọn bước làm hiện tại và đưa ra câu hỏi gợi mở hoặc nhắc lại định lý/công thức cho bước kế tiếp.
                                3. TUYỆT ĐỐI KHÔNG đưa ra lời giải hoàn chỉnh hoặc đáp số số học cuối cùng.
                                4. Văn phong thân thiện, động viên.

                                YÊU CẦU ĐỊNH DẠNG KATEX / CÔNG THỨC TOÁN (RẤT QUAN TRỌNG):
                                1. TẤT CẢ công thức toán học, đại lượng, biến số (R, S, x, y, \\pi, \\alpha...), số đo và đơn vị (5\\text{ cm}, \\text{cm}^2...) BẮT BUỘC kẹp giữa 2 dấu đô-la $...$ (inline math) hoặc $$...$$ (block math).
                                   - Ví dụ ĐÚNG: $R = 5\\text{ cm}$, $S = \\pi R^2$, $3,14$, $\\text{cm}^2$, $x$, $y$.
                                2. QUY TẮC BỌC DẤU ĐÔ-LA $...$ VÀ DÙNG NGOẶC TRÒN:
                                   - Dấu $ CHỈ bọc TRỰC TIẾP công thức toán, TUYỆT ĐỐI KHÔNG bọc chữ tiếng Việt thông thường (ví dụ SAI CẤM: $lấy$, $với$).
                                   - TUYỆT ĐỐI KHÔNG lồng các dấu đô-la vào nhau (ví dụ SAI CẤM: $lấy $\\pi \\approx 3.14$$ -> ví dụ ĐÚNG: (lấy $\\pi \\approx 3.14$)).
                                   - Các dấu ngoặc tròn trong văn bản tiếng Việt dùng để chú thích là HOÀN TOÀN HỢP LỆ (ví dụ ĐÚNG: (lấy $\\pi \\approx 3.14$) hoặc (với $R = 5\\text{ cm}$)).
                                   - KHÔNG dùng ngoặc tròn hay \\(...\\) hay [...] để THAY THẾ cho dấu đô-la bọc công thức toán (ví dụ SAI: (R = 5\\text{ cm}) hay \\(R = 5\\text{ cm}\\) thay vì $R = 5\\text{ cm}$).

                                YÊU CẦU ĐỊNH DẠNG JSON BẮT BUỘC:
                                Phản hồi CHỈ trả về duy nhất một JSON Object hợp lệ, KHÔNG kèm văn bản hay giải thích bên ngoài, KHÔNG kẹp trong markdown fence ```json, đúng schema sau:
                                {
                                  "analysis": "Nhận xét ngắn gọn tiến độ bài làm hiện tại của học sinh",
                                  "hintContent": "Gợi ý tư duy ngắn (từ 50 đến 120 từ) định hướng bước tiếp theo bằng tiếng Việt, dùng Markdown và KaTeX ($...$)"
                                }
                                """;

                String defaultGradingPrompt = """
                                Bạn là một giáo viên môn {{subject}} xuất sắc, công tâm và giàu kinh nghiệm.
                                Nhiệm vụ của bạn là chấm điểm sơ bộ bài làm tự luận và đánh giá hình vẽ Canvas (nếu có) của học sinh.

                                [ĐỀ BÀI TOÁN]:
                                - Tiêu đề: {{title}}
                                - Thang điểm tối đa: {{max_score}}
                                - Nội dung đề (nếu có hình vẽ Canvas mẫu thì nằm trong comment <!-- DRAWINGS_DATA_START -->):
                                {{problem_content}}

                                [BÀI LÀM CỦA HỌC SINH] (hình vẽ Canvas học sinh vẽ nếu có nằm trong comment <!-- DRAWINGS_DATA_START -->):
                                {{student_content}}

                                Nhiệm vụ chi tiết:
                                1. So sánh hình vẽ Canvas của học sinh với hình mẫu trong đề bài (nếu có), liệt kê các lỗi sai cụ thể trong danh sách drawingIssues (ví dụ: vẽ thiếu đường cao, sai góc, sai tiệm cận đồ thị). Nếu bài tập không có hình mẫu hoặc học sinh không vẽ hình thì để drawingIssues = [].
                                2. Chấm điểm sơ bộ bài tự luận theo thang điểm {{max_score}} và viết DỰ THẢO lời nhận xét chi tiết bằng tiếng Việt, chỉ ra từng lỗi sai cụ thể trong lời giải.

                                YÊU CẦU ĐỊNH DẠNG KATEX / CÔNG THỨC TOÁN (RẤT QUAN TRỌNG):
                                1. TẤT CẢ công thức toán học, đại lượng, biến số (R, S, x, y, \\pi, \\alpha...), số đo và đơn vị (5\\text{ cm}, \\text{cm}^2...) trong draftFeedback BẮT BUỘC kẹp giữa 2 dấu đô-la $...$ (inline math) hoặc $$...$$ (block math).
                                   - Ví dụ ĐÚNG: $R = 5\\text{ cm}$, $S = \\pi R^2$, $3,14$, $\\text{cm}^2$, $x$, $y$.
                                2. QUY TẮC BỌC DẤU ĐÔ-LA $...$ VÀ DÙNG NGOẶC TRÒN:
                                   - Dấu $ CHỈ bọc TRỰC TIẾP công thức toán, TUYỆT ĐỐI KHÔNG bọc chữ tiếng Việt thông thường (ví dụ SAI CẤM: $lấy$, $với$).
                                   - TUYỆT ĐỐI KHÔNG lồng các dấu đô-la vào nhau (ví dụ SAI CẤM: $lấy $\\pi \\approx 3.14$$ -> ví dụ ĐÚNG: (lấy $\\pi \\approx 3.14$)).
                                   - Các dấu ngoặc tròn trong văn bản tiếng Việt dùng để chú thích là HOÀN TOÀN HỢP LỆ (ví dụ ĐÚNG: (lấy $\\pi \\approx 3.14$) hoặc (với $R = 5\\text{ cm}$)).
                                   - KHÔNG dùng ngoặc tròn hay \\(...\\) hay [...] để THAY THẾ cho dấu đô-la bọc công thức toán (ví dụ SAI: (R = 5\\text{ cm}) hay \\(R = 5\\text{ cm}\\) thay vì $R = 5\\text{ cm}$).

                                YÊU CẦU ĐỊNH DẠNG JSON BẮT BUỘC:
                                Phản hồi CHỈ trả về duy nhất một JSON Object hợp lệ, KHÔNG kèm văn bản hay giải thích bên ngoài, KHÔNG kẹp trong markdown fence ```json, đúng schema sau:
                                {
                                  "suggestedScore": 8.5,
                                  "draftFeedback": "Lời nhận xét chi tiết dùng Markdown và KaTeX ($...$)",
                                  "drawingIssues": [
                                    { "issue": "Tên lỗi ngắn gọn", "detail": "Mô tả chi tiết lỗi vẽ hình" }
                                  ]
                                }
                                """;

                String defaultQuestionGenPrompt = """
                                Bạn là một chuyên gia Toán học và biên soạn đề thi xuất sắc.
                                Nhiệm vụ của bạn là sinh ra một bài toán chuẩn sư phạm theo đúng thông tin dưới đây:
                                - Khối lớp: {{grade_level}}
                                - Mức độ tư duy: {{difficulty}}
                                - Chủ đề: {{topic}}
                                - Dạng bài: {{question_type}}

                                YÊU CẦU ĐỊNH DẠNG KATEX / CÔNG THỨC TOÁN (RẤT QUAN TRỌNG):
                                1. TẤT CẢ công thức toán học, đại lượng, biến số (R, S, x, y, \\pi, \\alpha...), số đo và đơn vị (6\\text{ cm}, \\text{cm}^2...) trong 'content' và 'explanation' BẮT BUỘC kẹp giữa 2 dấu đô-la $...$ (inline math) hoặc $$...$$ (block math).
                                   - Ví dụ ĐÚNG: $x^2 + 2x + 1 = 0$, $\\frac{a}{b}$, $AB = 6\\text{ cm}$, $AC = 8\\text{ cm}$, $S = \\pi R^2$, $\\pi \\approx 3.14$, $S = 24\\text{ cm}^2$.
                                   - TUYỆT ĐỐI KHÔNG để mất dấu gạch chéo \\ khi viết \\text{ cm} hay \\frac{a}{b}.
                                2. QUY TẮC BỌC DẤU ĐÔ-LA $...$ VÀ DÙNG NGOẶC TRÒN:
                                   - Dấu $ CHỈ bọc TRỰC TIẾP công thức toán, TUYỆT ĐỐI KHÔNG bọc chữ tiếng Việt thông thường (ví dụ SAI CẤM: $lấy$, $với$).
                                   - TUYỆT ĐỐI KHÔNG lồng các dấu đô-la vào nhau (ví dụ SAI CẤM: $lấy $\\pi \\approx 3.14$$ -> ví dụ ĐÚNG: (lấy $\\pi \\approx 3.14$)).
                                   - Các dấu ngoặc tròn trong văn bản tiếng Việt dùng để chú thích là HOÀN TOÀN HỢP LỆ (ví dụ ĐÚNG: (lấy $\\pi \\approx 3.14$) hoặc (với $R = 5\\text{ cm}$)).
                                   - KHÔNG dùng ngoặc tròn hay \\(...\\) hay [...] để THAY THẾ cho dấu đô-la bọc công thức toán (ví dụ SAI: (R = 5\\text{ cm}) hay \\(R = 5\\text{ cm}\\) thay vì $R = 5\\text{ cm}$).

                                Yêu cầu nội dung bổ sung:
                                1. CHÚ Ý YÊU CẦU vẽ hình/đồ thị: {{canvas_requirement}}
                                2. CHÚ Ý YÊU CẦU lời giải chi tiết: {{explanation_requirement}}
                                3. Về định dạng văn bản và xuống dòng:
                                   - Trình bày các bước giải trong 'explanation' và các ý trong 'content' rõ ràng, tách thành từng đoạn văn xuống dòng mạch lạc.
                                   - Sử dụng dấu ngắt dòng tiêu chuẩn của Markdown/JSON, tuyệt đối không để sót các chuỗi ký tự thô "\\n" hay "/n" dính liền vào văn bản.
                                4. Trả về ĐÚNG MỘT JSON OBJECT duy nhất, KHÔNG kèm theo văn bản giải thích ngoài JSON, KHÔNG dùng markdown block ```json.

                                JSON Schema quy định:
                                {
                                  "title": "Tiêu đề ngắn gọn cho bài toán",
                                  "content": "Nội dung đề bài chi tiết dạng Markdown + KaTeX",
                                  "explanation": "Lời giải chi tiết từng bước (nếu người dùng yêu cầu, ngược lại để rỗng \"\")",
                                  "canvasData": {
                                    "width": 500,
                                    "height": 400,
                                    "elements": [
                                      { "type": "point", "id": "O", "x": 0.0, "y": 0.0, "label": "O" },
                                      { "type": "point", "id": "A", "x": 3.0, "y": 0.0, "label": "A" },
                                      { "type": "point", "id": "B", "x": 1.5, "y": 2.598, "label": "B" },
                                      { "type": "circle", "id": "c1", "centerId": "O", "radius": 3.0, "pointId": "A" },
                                      { "type": "segment", "id": "s1", "fromId": "O", "toId": "A" },
                                      { "type": "segment", "id": "s2", "fromId": "O", "toId": "B" }
                                    ]
                                  }
                                }
                                Lưu ý quan trọng cho hình vẽ (canvasData):
                                - Tọa độ (x, y) của tất cả điểm BẮT BUỘC nằm trong hệ tọa độ Đề-các nhỏ chuẩn mực từ -6.0 đến 6.0 (Ví dụ: A(-2, 3), B(3, 3), C(4, -1), D(-1, -1)). TUYỆT ĐỐI KHÔNG dùng tọa độ dạng pixel (như 100..500) hay số quá lớn (> 15).
                                - Khi đề toán có đồ thị hàm số (parabol, đường thẳng, hàm số...): Tạo element có `type: "functiongraph"`, `id: "fg1"`, và `parsedFunc` là biểu thức hàm số theo biến x (ví dụ: `x**2 - 2*x + 1`, `2*x - 3`, `-x**2 + 4`). Đồng thời có thể tạo thêm các điểm đỉnh Parabol, điểm thuộc đồ thị (dạng "point").
                                - Khi đề toán có đường tròn, BẮT BUỘC phải tạo điểm tâm (dạng "point"), tạo các điểm trên đường tròn, và thêm phần tử "circle" với "centerId" và "radius" hoặc "pointId".
                                """;

                String defaultHandwritingPrompt = """
                                Bạn là trợ lý OCR nhận diện chữ viết tay công thức toán học chuyên nghiệp.
                                Nhiệm vụ: Phân tích hình ảnh chữ viết tay/công thức toán này và chuyển đổi thành mã LaTeX tương ứng.
                                QUY TẮC BẮT BỘC:
                                1. Nếu hình ảnh KHÔNG chứa chữ viết tay, công thức toán hoặc không có văn bản nào, BẮT BỘC chỉ trả về duy nhất chuỗi: NO_HANDWRITING_DETECTED
                                2. Nếu hình ảnh có nhiều dòng chữ hoặc công thức toán, BẮT BỘC bọc toàn bộ các dòng trong môi trường \\begin{aligned} ... \\end{aligned} và dùng \\\\ để xuống dòng.
                                3. Chỉ trả về chuỗi mã LaTeX nguyên bản (ví dụ: \\begin{aligned} x &= 1 \\\\ y &= 2 \\end{aligned}), KHÔNG kèm theo bất kỳ văn bản giải thích hay Markdown code block (như ```latex) nào khác.
                                """;

                String defaultSketchPrompt = """
                                Bạn là chuyên gia AI phân tích nét vẽ phác thảo hình học và đồ thị hàm số, chuyển thành dữ liệu JSXGraph JSON.
                                QUY TẮC BẮT BỘC:
                                1. Nếu hình ảnh KHÔNG chứa bất kỳ hình phác thảo hình học hoặc đồ thị hàm số nào (ví dụ: ảnh màu linh tinh, hoặc không có hình vẽ), BẮT BỘC chỉ trả về duy nhất 1 chuỗi JSON: {"error": "NO_GEOMETRY_DETECTED", "shapeType": "NO_GEOMETRY", "elements": []}
                                2. Nếu tìm thấy nét vẽ phác thảo hình học hoặc đồ thị hàm số, hãy nắn chỉnh thành cấu trúc hình học chuẩn JSXGraph và trả về duy nhất 1 đối tượng JSON nguyên bản (KHÔNG bọc trong markdown codeblock):
                                - Đối với hình học phẳng (tam giác, tứ giác, hình tròn...):
                                {
                                  "shapeType": "TRIANGLE_RIGHT" | "TRIANGLE_EQUAL" | "CIRCLE" | "RECTANGLE" | "POLYGON",
                                  "boundingbox": [-5, 5, 5, -5],
                                  "axis": true,
                                  "grid": true,
                                  "elements": [
                                    {"type": "point", "id": "A", "label": "A", "x": 0, "y": 4},
                                    {"type": "point", "id": "B", "label": "B", "x": 0, "y": 0},
                                    {"type": "point", "id": "C", "label": "C", "x": 3, "y": 0},
                                    {"type": "segment", "from": "A", "to": "B"},
                                    {"type": "segment", "from": "B", "to": "C"},
                                    {"type": "segment", "from": "C", "to": "A"}
                                  ]
                                }
                                - Đối với đồ thị hàm số (parabol, đường thẳng, hàm số...):
                                {
                                  "shapeType": "FUNCTION_GRAPH",
                                  "boundingbox": [-5, 5, 5, -5],
                                  "axis": true,
                                  "grid": true,
                                  "elements": [
                                    {"type": "functiongraph", "id": "fg1", "parsedFunc": "-(x-2)**2 + 2"},
                                    {"type": "point", "id": "I", "label": "I", "x": 2, "y": 2}
                                  ]
                                }
                                3. QUY TẮC NGUYÊN TẮC KHAI BÁO ĐIỂM: Với mọi đối tượng segment, line, circle, polygon, BẮT BỘC mọi điểm (như from, to, center, pointOnCircle, vertices) được tham chiếu PHẢI được định nghĩa trước dưới dạng phần tử `{"type": "point", "id": "...", "label": "...", "x": ..., "y": ...}`. Tuyệt đối không để điểm tham chiếu bị thiếu tọa độ x, y.
                                4. Tọa độ các điểm và miền vẽ phải nằm trong hệ tọa độ Đề-các chuẩn [-6, 6].
                                """;

                String defaultBatchQuestionGenPrompt = """
                                Bạn là một chuyên gia Sư phạm Toán học.
                                Nhiệm vụ của bạn là đọc và phân tích toàn bộ tài liệu/đề thi dưới đây (được trích xuất từ file đề thi Word/PDF/Text):
                                \"\"\"
                                {{document_content}}
                                \"\"\"

                                YÊU CẦU PHÂN TÍCH & TÁCH THÀNH CÁC BÀI TẬP LẺ ĐỘC LẬP:
                                1. Phân tích tài liệu, tự động nhận diện và bóc tách từng câu hỏi/bài toán thành một BÀI TẬP RIÊNG BIỆT (ví dụ nếu file có 3 câu thì tách thành 3 bài tập riêng lẻ).
                                2. Tự động đặt Tiêu đề ngắn gọn, súc tích cho từng bài tập lẻ đó dựa trên nội dung câu hỏi (ví dụ: "Bài 1: Rút gọn biểu thức", "Bài 2: Giải hệ phương trình", "Bài 3: Tính diện tích tam giác"...).
                                3. Nếu trong tài liệu có các thẻ mã ảnh như [IMAGE_...], hãy giữ nguyên đúng vị trí của thẻ ảnh đó trong nội dung bài tập tương ứng.
                                4. Đối với từng bài tập trong danh sách 'questions':
                                   - "id": Mã bài tập ngắn (ví dụ: "q1", "q2",...).
                                   - "title": Tên đề mục / tiêu đề ngắn gọn cho bài tập lẻ (ví dụ: "Bài 1: Rút gọn biểu thức và tính giá trị", "Bài 2: Giải hệ phương trình bậc nhất hai ẩn", "Bài 3: Bài toán thực tế hình học"...).
                                   - "content": Toàn bộ nội dung đề bài chi tiết của bài tập đó (dạng Markdown + công thức KaTeX). Tuyệt đối KHÔNG kèm lời giải, chỉ lấy nội dung đề bài để học sinh làm.

                                YÊU CẦU ĐỊNH DẠNG KATEX / CÔNG THỨC TOÁN (RẤT QUAN TRỌNG):
                                1. TẤT CẢ công thức toán học, đại lượng, biến số (R, S, x, y, \\pi, \\alpha...), số đo và đơn vị (6\\text{ cm}, \\text{cm}^2...) trong 'content' BẮT BUỘC kẹp giữa 2 dấu đô-la $...$ (inline math) hoặc $$...$$ (block math).
                                   - Ví dụ ĐÚNG: $x^2 + 2x + 1 = 0$, $\\frac{a}{b}$, $AB = 6\\text{ cm}$, $AC = 8\\text{ cm}$, $S = \\pi R^2$, $\\pi \\approx 3.14$, $S = 24\\text{ cm}^2$.
                                   - TUYỆT ĐỐI KHÔNG để mất dấu gạch chéo \\ khi viết \\text{ cm} hay \\frac{a}{b}.
                                2. Dấu $ CHỈ bọc TRỰC TIẾP công thức toán, TUYỆT ĐỐI KHÔNG bọc chữ tiếng Việt thông thường.
                                3. TUYỆT ĐỐI KHÔNG dùng ngoặc tròn hay \\(...\\) hay [...] để THAY THẾ cho dấu đô-la bọc công thức toán.

                                YÊU CẦU ĐỊNH DẠNG JSON BẮT BUỘC:
                                Phản hồi CHỈ trả về duy nhất một JSON Object hợp lệ, KHÔNG kèm văn bản hay giải thích bên ngoài, KHÔNG kẹp trong markdown fence ```json, đúng schema sau:
                                {
                                  "suggestedTitle": "Đề thi khảo sát chất lượng Toán",
                                  "questions": [
                                    {
                                      "id": "q1",
                                      "title": "Bài 1: Rút gọn biểu thức chứa căn",
                                      "content": "Nội dung câu hỏi 1 với công thức KaTeX $...$"
                                    }
                                  ]
                                }
                                """;

                upsertSystemPrompt("PROMPT_STUDENT_HINT", "Prompt Gợi ý Tư duy Làm bài", "STUDENT_HINT",
                                defaultHintPrompt,
                                "title,problem_content,student_content,subject",
                                "Đưa ra gợi ý định hướng từng bước theo phương pháp Socratic, tuyệt đối không cho đáp án trực tiếp.");
                upsertSystemPrompt("PROMPT_HANDWRITING_LATEX", "Prompt Nhận diện Chữ viết tay sang LaTeX",
                                "CANVAS_LATEX",
                                defaultHandwritingPrompt,
                                "",
                                "Phân tích ảnh chữ viết tay/công thức toán và trích xuất mã LaTeX/KaTeX hợp lệ.");
                upsertSystemPrompt("PROMPT_SKETCH_GEOMETRY", "Prompt Nắn chỉnh Nét vẽ Phác thảo sang JSXGraph Canvas",
                                "CANVAS_LATEX",
                                defaultSketchPrompt,
                                "",
                                "Phân tích ảnh nét vẽ phác thảo hình học/đồ thị hàm số và chuyển đổi thành cấu trúc JSON JSXGraph chuẩn.");
                upsertSystemPrompt("PROMPT_SUBMISSION_GRADING", "Prompt Chấm bài tự luận tự động", "SUBMISSION_GRADING",
                                defaultGradingPrompt,
                                "title,problem_content,student_content,max_score,subject",
                                "Chấm điểm và nhận xét chi tiết bài làm tự luận.");
                upsertSystemPrompt("PROMPT_QUESTION_GEN", "Prompt Sinh Bài tập Toán", "QUESTION_GEN",
                                defaultQuestionGenPrompt,
                                "grade_level,difficulty,topic,question_type,canvas_requirement,explanation_requirement",
                                "Tự động tạo bài tập tự luận môn Toán.");
                upsertSystemPrompt("PROMPT_BATCH_QUESTION_GEN", "Prompt Tạo Hàng Loạt Bài Tập từ Tài Liệu", "BATCH_QUESTION_GEN",
                                defaultBatchQuestionGenPrompt,
                                "grade_level,topic,canvas_requirement,explanation_requirement,document_content",
                                "Tự động phân tích và bóc tách tài liệu/file đề thi thành danh sách các bài tập toán.");
        }

        private void upsertSystemPrompt(String code, String name, String taskCode, String defaultContent,
                        String allowedVariables, String description) {
                Optional<SystemPrompt> existingOpt = systemPromptRepository.findByCode(code);
                if (existingOpt.isPresent()) {
                        SystemPrompt existing = existingOpt.get();
                        existing.syncMetadata(name, taskCode, defaultContent, allowedVariables, description);
                        if (existing.getCurrentContent() == null || existing.getCurrentContent().isBlank()
                                || !existing.getCurrentContent().contains("{{explanation_requirement}}")
                                || existing.getCurrentContent().contains("CHỈ sinh ra nội dung lời giải chi tiết KHI yêu cầu (prompt)")) {
                                existing.setCurrentContent(defaultContent);
                        }
                        systemPromptRepository.save(existing);
                        log.info("[DatabaseSeeder] Synchronized system prompt metadata: {} ({})", code, name);
                } else {
                        SystemPrompt p = SystemPrompt.builder()
                                        .code(code)
                                        .name(name)
                                        .taskCode(taskCode)
                                        .defaultContent(defaultContent)
                                        .currentContent(defaultContent)
                                        .allowedVariables(allowedVariables)
                                        .description(description)
                                        .status(SystemPromptStatus.ACTIVE)
                                        .build();
                        SystemPrompt savedP = systemPromptRepository.save(p);
                        systemPromptHistoryRepository.save(SystemPromptHistory.builder()
                                        .prompt(savedP)
                                        .version(1)
                                        .content(savedP.getDefaultContent())
                                        .changeReason("Khởi tạo System Prompt ban đầu")
                                        .createdBy("SYSTEM")
                                        .build());
                        log.info("[DatabaseSeeder] Initialized new system prompt: {} ({})", code, name);
                }
        }

        private static Permission p(String name, String description) {
                return Permission.builder().name(name).description(description).build();
        }

        private User createUser(String email, String fullName, String password, String phone, Role role, Gender gender,
                        String avatarUrl) {
                User user = userRepository.save(User.builder()
                                .email(email)
                                .fullName(fullName)
                                .password(passwordEncoder.encode(password))
                                .phoneNumber(phone)
                                .role(role)
                                .isActive(true)
                                .dateOfBirth(LocalDate.of(1995, 1, 1))
                                .gender(gender)
                                .avatarUrl(avatarUrl)
                                .build());

                NotificationSettings settings = NotificationSettings.builder()
                                .userId(user.getId())
                                .masterEmail(true)
                                .teacherJoinRequest(true)
                                .teacherNewSubmission(true)
                                .studentNewAssignment(true)
                                .studentGraded(true)
                                .studentDeadlineReminder(true)
                                .build();
                notificationSettingsRepository.save(settings);

                return user;
        }
}
