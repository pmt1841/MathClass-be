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
                                Permission.builder().name("classroom:create").description("Tạo lớp học").build(),
                                Permission.builder().name("classroom:update").description("Sửa lớp học").build(),
                                Permission.builder().name("classroom:delete").description("Xóa lớp học").build(),
                                Permission.builder().name("classroom:manage_requests")
                                                .description("Quản lý yêu cầu tham gia").build(),
                                Permission.builder().name("classroom:remove_student").description("Xóa học sinh")
                                                .build(),
                                Permission.builder().name("classroom:join").description("Tham gia lớp").build(),
                                Permission.builder().name("classroom:join_status")
                                                .description("Xem trạng thái tham gia").build(),

                                Permission.builder().name("assignment:create").description("Tạo bài tập").build(),
                                Permission.builder().name("assignment:update").description("Sửa bài tập").build(),
                                Permission.builder().name("assignment:delete").description("Xóa bài tập").build(),
                                Permission.builder().name("assignment:publish").description("Xuất bản bài tập").build(),
                                Permission.builder().name("assignment:read").description("Xem bài tập").build(),

                                Permission.builder().name("submission:submit").description("Nộp bài").build(),
                                Permission.builder().name("submission:read_own").description("Xem bài nộp của mình")
                                                .build(),
                                Permission.builder().name("submission:grade").description("Chấm điểm").build(),
                                Permission.builder().name("submission:read_all").description("Xem tất cả bài nộp")
                                                .build(),
                                Permission.builder().name("submission:comment").description("Bình luận bài nộp")
                                                .build(),

                                Permission.builder().name("dashboard:teacher_view")
                                                .description("Xem thống kê giáo viên").build(),
                                Permission.builder().name("dashboard:student_view").description("Xem thống kê học sinh")
                                                .build(),

                                Permission.builder().name("library:read").description("Xem thư viện bài tập dùng chung")
                                                .build(),
                                Permission.builder().name("library:clone").description("Clone bài tập từ thư viện")
                                                .build(),

                                Permission.builder().name("user:manage").description("Quản lý người dùng").build());

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
                                "ASSIGNMENT_GRADING", 5);
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
                                .students(new HashSet<>(Arrays.asList(student1, student2, student3)))
                                .build());

                Classroom class2 = classroomRepository.save(Classroom.builder()
                                .classCode("MATH102")
                                .className("Lớp Toán Hình học 11")
                                .teacher(teacher2)
                                .maxStudents(25)
                                .description("Lớp học chuyên đề hình học không gian và phương pháp tọa độ lớp 11.")
                                .students(new HashSet<>(Arrays.asList(student3, student4, student5)))
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
                Map<String, Object> graphData = new HashMap<>();
                graphData.put("type", "parabola");
                graphData.put("a", 1.0);
                graphData.put("b", -4.0);
                graphData.put("c", 3.0);

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
                Map<String, Object> drawingData = new HashMap<>();
                drawingData.put("points", Arrays.asList(Map.of("name", "I", "x", 2.0, "y", -1.0)));
                Map<String, Object> drawingMeta = new HashMap<>();
                drawingMeta.put("tool", "JSXGraph");
                drawingMeta.put("version", "1.4.2");

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

                // 7. Create System Prompts
                seedSystemPrompts();
        }

        private void seedSystemPrompts() {
                if (systemPromptRepository.count() > 0) {
                        return;
                }
                log.info("[DatabaseSeeder] Creating sample system prompts...");

                String defaultHintPrompt = """
                                Bạn là một trợ lý giáo viên môn {{subject}} xuất sắc và kiên nhẫn.
                                Nhiệm vụ của bạn là đưa ra 01 GỢI Ý TƯ DUY NGẮN (từ 50 đến 120 từ) định hướng BƯỚC TIẾP THEO bám sát chính xác bài toán dưới đây.

                                [ĐỀ BÀI CẦN GIẢI]:
                                - Tiêu đề: {{title}}
                                - Nội dung đề bài: {{problem_content}}

                                [TIẾN ĐỘ BÀI LÀM HIỆN TẠI CỦA HỌC SINH]:
                                {{student_content}}

                                YÊU CẦU BẮT BUỘC:
                                1. Đọc và phân tích kỹ bài toán cụ thể nêu ở trên.
                                2. Phân tích tiến độ bài làm của học sinh:
                                   - Nếu bài làm trống: hãy chỉ rõ giả thiết, phương pháp hoặc công thức đầu tiên học sinh cần áp dụng để bắt đầu.
                                   - Nếu học sinh đã viết bài làm: hãy nhận xét ngắn gọn bước làm hiện tại và đưa ra câu hỏi gợi mở hoặc nhắc lại định lý/công thức cho bước kế tiếp.
                                3. TUYỆT ĐỐI KHÔNG đưa ra lời giải hoàn chỉnh hoặc đáp số số học cuối cùng.
                                4. Trả lời trực tiếp vào gợi ý bằng tiếng Việt, văn phong thân thiện, động viên. Dùng KaTeX cho công thức toán (ví dụ: $x^2 + 1$).
                                """;

                String defaultLatexPrompt = """
                                Bạn là một chuyên gia xử lý định dạng công thức toán học và biểu thức đại số.
                                Nhiệm vụ của bạn là chuyển đổi và chuẩn hóa biểu thức toán học được cung cấp sang định dạng {{math_format}} chính xác.

                                [THÔNG TIN BIỂU THỨC CẦN XỬ LÝ]:
                                - Biểu thức gốc: {{math_expression}}
                                - Định dạng mục tiêu: {{math_format}}

                                YÊU CẦU BẮT BUỘC:
                                1. Đọc và phân tích chính xác các ký hiệu toán học trong biểu thức gốc {{math_expression}}.
                                2. Chuyển đổi biểu thức sang cú pháp {{math_format}} chuẩn (ví dụ: dùng KaTeX/LaTeX với các dấu $, \\frac{}{}, \\sqrt{}, v.v.).
                                3. Kiểm tra kỹ tính hợp lệ của cú pháp, đảm bảo các dấu đóng/mở ngoặc và ký hiệu toán học chính xác.
                                4. Chỉ trả về duy nhất chuỗi biểu thức toán học đã chuyển đổi, tuyệt đối không kèm lời giải thích hay văn bản thừa nào khác.
                                """;

                String defaultGradingPrompt = """
                                Bạn là một giáo viên môn {{subject}} lớp {{grade_level}} xuất sắc, công tâm và giàu kinh nghiệm.
                                Nhiệm vụ của bạn là chấm điểm và đưa ra nhận xét chi tiết bài làm tự luận của học sinh môn {{subject}}.

                                [THÔNG TIN ĐỀ BÀI VÀ BÀI LÀM]:
                                - Khối lớp: {{grade_level}}
                                - Môn học: {{subject}}
                                - Câu hỏi / Đề bài: {{question_content}}
                                - Đáp án / Hướng dẫn chấm chuẩn: {{correct_answer}}
                                - Bài làm của học sinh: {{student_answer}}
                                - Thang điểm tối đa: {{max_score}}

                                YÊU CẦU BẮT BUỘC:
                                1. Đọc kỹ đề bài {{question_content}} và so sánh bài làm của học sinh {{student_answer}} với đáp án chuẩn {{correct_answer}}.
                                2. Phân tích chi tiết các bước giải:
                                   - Chỉ ra các bước đúng, lập luận logic và công thức chính xác học sinh đã áp dụng.
                                   - Phát hiện các lỗi sai (nếu có): sai sót số học, thiếu điều kiện xác định, lập luận thiếu căn cứ hoặc trình bày chưa chặt chẽ.
                                3. Đánh giá và cho điểm chính xác trên thang điểm tối đa {{max_score}}.
                                4. Nhận xét mang tính xây dựng, động viên học sinh và hướng dẫn cách khắc phục lỗi sai. Dùng KaTeX cho mọi công thức toán học.
                                """;

                String defaultQuestionGenPrompt = """
                                Bạn là một chuyên gia Toán học và biên soạn đề thi xuất sắc.
                                Nhiệm vụ của bạn là sinh ra một bài toán chuẩn sư phạm theo đúng thông tin dưới đây:
                                - Khối lớp: {{grade_level}}
                                - Mức độ tư duy: {{difficulty}}
                                - Chủ đề: {{topic}}
                                - Dạng bài: {{question_type}}

                                Yêu cầu định dạng bắt buộc:
                                1. Tất cả công thức toán học phải viết dạng KaTeX kẹp giữa dấu $...$ (inline) hoặc $$...$$ (block math). Ví dụ: $x^2 + 2x + 1 = 0$, $\\frac{a}{b}$.
                                {{canvas_requirement}}
                                3. Về phần lời giải ('explanation'): CHỈ sinh ra nội dung lời giải chi tiết KHI yêu cầu (prompt) của người dùng có đề nghị/nhắc tới việc cung cấp lời giải (ví dụ: 'kèm lời giải', 'giải chi tiết', 'hướng dẫn giải', 'trình bày giải'). Nếu người dùng KHÔNG yêu cầu lời giải, hãy để trường 'explanation' là chuỗi rỗng "".
                                4. Trả về ĐÚNG MỘT JSON OBJECT duy nhất, KHÔNG kèm theo văn bản giải thích ngoài JSON, KHÔNG dùng markdown block ```json.

                                JSON Schema quy định:
                                {
                                  "title": "Tiêu đề ngắn gọn cho bài toán",
                                  "content": "Nội dung đề bài chi tiết dạng Markdown + KaTeX",
                                  "explanation": "Lời giải chi tiết từng bước (nếu người dùng yêu cầu, ngược lại để rỗng \"\")",
                                  "grade": {{grade_level}},
                                  "difficulty": "{{difficulty_code}}",
                                  "topic": "{{topic}}",
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

                SystemPrompt p1 = SystemPrompt.builder()
                                .code("PROMPT_STUDENT_HINT")
                                .name("Prompt Gợi ý Tư duy Làm bài")
                                .taskCode("STUDENT_HINT")
                                .defaultContent(defaultHintPrompt)
                                .currentContent(defaultHintPrompt)
                                .allowedVariables("title,problem_content,student_content,subject")
                                .description("Đưa ra gợi ý định hướng từng bước theo phương pháp Socratic, tuyệt đối không cho đáp án trực tiếp.")
                                .status(SystemPromptStatus.ACTIVE)
                                .build();
                SystemPrompt savedP1 = systemPromptRepository.save(p1);
                systemPromptHistoryRepository.save(SystemPromptHistory.builder()
                                .prompt(savedP1)
                                .version(1)
                                .content(savedP1.getDefaultContent())
                                .changeReason("Khởi tạo System Prompt ban đầu")
                                .createdBy("SYSTEM")
                                .build());

                SystemPrompt p2 = SystemPrompt.builder()
                                .code("PROMPT_LATEX_CANVAS")
                                .name("Prompt Ép chuẩn mã LaTeX / Canvas")
                                .taskCode("LATEX_CANVAS_FORMAT")
                                .defaultContent(defaultLatexPrompt)
                                .currentContent(defaultLatexPrompt)
                                .allowedVariables("math_expression,math_format")
                                .description("Đảm bảo AI chỉ trả về mã LaTeX/KaTeX hợp lệ.")
                                .status(SystemPromptStatus.ACTIVE)
                                .build();
                SystemPrompt savedP2 = systemPromptRepository.save(p2);
                systemPromptHistoryRepository.save(SystemPromptHistory.builder()
                                .prompt(savedP2)
                                .version(1)
                                .content(savedP2.getDefaultContent())
                                .changeReason("Khởi tạo System Prompt ban đầu")
                                .createdBy("SYSTEM")
                                .build());

                SystemPrompt p3 = SystemPrompt.builder()
                                .code("PROMPT_SUBMISSION_GRADING")
                                .name("Prompt Chấm bài tự luận tự động")
                                .taskCode("SUBMISSION_GRADING")
                                .defaultContent(defaultGradingPrompt)
                                .currentContent(defaultGradingPrompt)
                                .allowedVariables(
                                                "grade_level,subject,question_content,correct_answer,student_answer,max_score")
                                .description("Chấm điểm và nhận xét chi tiết bài làm tự luận.")
                                .status(SystemPromptStatus.ACTIVE)
                                .build();
                SystemPrompt savedP3 = systemPromptRepository.save(p3);
                systemPromptHistoryRepository.save(SystemPromptHistory.builder()
                                .prompt(savedP3)
                                .version(1)
                                .content(savedP3.getDefaultContent())
                                .changeReason("Khởi tạo System Prompt ban đầu")
                                .createdBy("SYSTEM")
                                .build());

                SystemPrompt p4 = SystemPrompt.builder()
                                .code("PROMPT_QUESTION_GEN")
                                .name("Prompt Sinh Bài tập Toán")
                                .taskCode("QUESTION_GEN")
                                .defaultContent(defaultQuestionGenPrompt)
                                .currentContent(defaultQuestionGenPrompt)
                                .allowedVariables(
                                                "grade_level,difficulty,difficulty_code,topic,question_type,canvas_requirement")
                                .description("Tự động tạo bài tập tự luận môn Toán.")
                                .status(SystemPromptStatus.ACTIVE)
                                .build();
                SystemPrompt savedP4 = systemPromptRepository.save(p4);
                systemPromptHistoryRepository.save(SystemPromptHistory.builder()
                                .prompt(savedP4)
                                .version(1)
                                .content(savedP4.getDefaultContent())
                                .changeReason("Khởi tạo System Prompt ban đầu")
                                .createdBy("SYSTEM")
                                .build());
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
