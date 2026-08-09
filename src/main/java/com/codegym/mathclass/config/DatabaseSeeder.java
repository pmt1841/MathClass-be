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
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.repository.SystemPromptHistoryRepository;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;

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
    private final TaskConfigRepository taskConfigRepository;
    private final ProviderRepository providerRepository;
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

        seedAiTaskConfigs();

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
                Permission.builder().name("classroom:manage_requests").description("Quản lý yêu cầu tham gia").build(),
                Permission.builder().name("classroom:remove_student").description("Xóa học sinh").build(),
                Permission.builder().name("classroom:join").description("Tham gia lớp").build(),
                Permission.builder().name("classroom:join_status").description("Xem trạng thái tham gia").build(),

                Permission.builder().name("assignment:create").description("Tạo bài tập").build(),
                Permission.builder().name("assignment:update").description("Sửa bài tập").build(),
                Permission.builder().name("assignment:delete").description("Xóa bài tập").build(),
                Permission.builder().name("assignment:publish").description("Xuất bản bài tập").build(),
                Permission.builder().name("assignment:read").description("Xem bài tập").build(),

                Permission.builder().name("submission:submit").description("Nộp bài").build(),
                Permission.builder().name("submission:read_own").description("Xem bài nộp của mình").build(),
                Permission.builder().name("submission:grade").description("Chấm điểm").build(),
                Permission.builder().name("submission:read_all").description("Xem tất cả bài nộp").build(),
                Permission.builder().name("submission:comment").description("Bình luận bài nộp").build(),

                Permission.builder().name("dashboard:teacher_view").description("Xem thống kê giáo viên").build(),
                Permission.builder().name("dashboard:student_view").description("Xem thống kê học sinh").build(),

                Permission.builder().name("library:read").description("Xem thư viện bài tập dùng chung").build(),
                Permission.builder().name("library:clone").description("Clone bài tập từ thư viện").build(),

                Permission.builder().name("user:manage").description("Quản lý người dùng").build()
        );

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
                    newRolePermissions.add(RolePermission.builder().role(role).permission(p).build());
                }
            }
        }

        if (!newRolePermissions.isEmpty()) {
            rolePermissionRepository.saveAll(newRolePermissions);
            log.info("[DatabaseSeeder] Assigned {} new role-permission mappings via batch insert.", newRolePermissions.size());
        }
    }

    private void seedAiTaskConfigs() {
        if (taskConfigRepository.findByTask("STUDENT_HINT").isEmpty()) {
            log.info("[DatabaseSeeder] Seeding default TaskConfig for STUDENT_HINT...");
            providerRepository.findAll().stream().findFirst().ifPresentOrElse(provider -> {
                com.codegym.mathclass.aiconfig.entity.TaskConfig studentHintConfig = com.codegym.mathclass.aiconfig.entity.TaskConfig.builder()
                        .task("STUDENT_HINT")
                        .provider(provider)
                        .model(provider.getProtocol() == com.codegym.mathclass.aiconfig.entity.ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE ? "gemini-1.5-flash" : "gpt-3.5-turbo")
                        .temperature(java.math.BigDecimal.valueOf(0.4))
                        .maxToken(512)
                        .enabled(true)
                        .build();
                taskConfigRepository.save(studentHintConfig);
                log.info("[DatabaseSeeder] Seeded STUDENT_HINT TaskConfig with Provider '{}'.", provider.getName());
            }, () -> log.warn("[DatabaseSeeder] No AI Provider found in DB. Skipping STUDENT_HINT default seeding."));
        }
    }

    private void seedAiCreditData() {
        seedCreditDefaults();
        seedCreditConfigs();
        seedCreditPackages();
        // Backfill cho các user đã tồn tại trước khi deploy tính năng credit (idempotent)
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
                log.info("[DatabaseSeeder] Seeded default credits for role {} = {}.", role, defaultCredits);
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
                log.info("[DatabaseSeeder] Backfilled tokensPerCredit={} for task {}.", defaultTokensPerCredit, task);
            }
        });
    }

    private void seedCreditPackages() {
        if (creditPackageRepository.count() == 0) {
            creditPackageRepository.saveAll(List.of(
                    CreditPackage.builder().name("Gói Cơ bản").credits(100).price(20000).enabled(true).sortOrder(1).build(),
                    CreditPackage.builder().name("Gói Pro").credits(300).price(50000).enabled(true).sortOrder(2).build(),
                    CreditPackage.builder().name("Gói VIP").credits(1000).price(150000).enabled(true).sortOrder(3).build()));
            log.info("[DatabaseSeeder] Seeded 3 default credit packages.");
        }
    }

    private void seedData() {
        // 1. Create Users (Admin, Teachers, Students)
        log.info("[DatabaseSeeder] Creating sample users...");
        User admin = createUser("admin@mathclass.com", "Admin Hệ Thống", "password123", "0901234567", Role.ADMIN,
                Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=admin");
        User teacher1 = createUser("teacher1@mathclass.com", "Thầy Nguyễn Văn A", "password123", "0902234567",
                Role.TEACHER, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=teacher1");
        User teacher2 = createUser("teacher2@mathclass.com", "Cô Trần Thị B", "password123", "0903234567", Role.TEACHER,
                Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=teacher2");

        User student1 = createUser("student1@mathclass.com", "Học sinh Nguyễn Văn An", "password123", "0904234567",
                Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student1");
        User student2 = createUser("student2@mathclass.com", "Học sinh Lê Thị Bình", "password123", "0905234567",
                Role.STUDENT, Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student2");
        User student3 = createUser("student3@mathclass.com", "Học sinh Phạm Văn Cường", "password123", "0906234567",
                Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student3");
        User student4 = createUser("student4@mathclass.com", "Học sinh Hoàng Thị Dung", "password123", "0907234567",
                Role.STUDENT, Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student4");
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
                .link("/assignments/" + assign1.getId() + "?classCode=" + assign1.getClassroom().getClassCode())
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

        SystemPrompt p1 = SystemPrompt.builder()
                .code("PROMPT_SOLVE_HINT")
                .name("Prompt Gợi ý giải toán từng bước")
                .taskCode("HINT_EXPLANATION")
                .defaultContent("Bạn là trợ lý giảng dạy môn {{subject}} cho học sinh {{grade_level}}. Khi nhận bài giải với câu hỏi: {{question_content}} và câu trả lời của học sinh: {{student_answer}}, hãy chỉ đưa ra gợi ý gợi mở hướng giải từng bước, tuyệt đối không cho đáp án trực tiếp.")
                .currentContent("Bạn là trợ lý giảng dạy môn {{subject}} cho học sinh {{grade_level}}. Khi nhận bài giải với câu hỏi: {{question_content}} và câu trả lời của học sinh: {{student_answer}}, hãy chỉ đưa ra gợi ý gợi mở hướng giải từng bước, tuyệt đối không cho đáp án trực tiếp.")
                .allowedVariables("grade_level,subject,student_answer,question_content")
                .description("Chỉ đưa ra gợi ý hướng giải, tuyệt đối không giải hộ đáp án chi tiết.")
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
                .defaultContent("Hãy chuyển đổi biểu thức toán sau: {{math_expression}} sang định dạng {{math_format}} chuẩn.")
                .currentContent("Hãy chuyển đổi biểu thức toán sau: {{math_expression}} sang định dạng {{math_format}} chuẩn.")
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
                .defaultContent("Bạn là giáo viên Toán lớp {{grade_level}}. Hãy chấm bài tập môn {{subject}} với câu hỏi: {{question_content}}, đáp án chuẩn: {{correct_answer}}, và bài làm học sinh: {{student_answer}}. Điểm tối đa: {{max_score}}.")
                .currentContent("Bạn là giáo viên Toán lớp {{grade_level}}. Hãy chấm bài tập môn {{subject}} với câu hỏi: {{question_content}}, đáp án chuẩn: {{correct_answer}}, và bài làm học sinh: {{student_answer}}. Điểm tối đa: {{max_score}}.")
                .allowedVariables("grade_level,subject,question_content,correct_answer,student_answer,max_score")
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
                .defaultContent("Bạn là chuyên gia biên soạn bài tập môn {{subject}} cho học sinh {{grade_level}}. Hãy khởi tạo bộ câu hỏi theo chủ đề: {{topic}}, mức độ khó: {{difficulty}}, với số lượng: {{question_count}} câu. Yêu cầu trả về kết quả dưới định dạng {{output_format}} và đảm bảo các công thức toán học được trình bày chuẩn theo dạng {{math_format}}.")
                .currentContent("Bạn là chuyên gia biên soạn bài tập môn {{subject}} cho học sinh {{grade_level}}. Hãy khởi tạo bộ câu hỏi theo chủ đề: {{topic}}, mức độ khó: {{difficulty}}, với số lượng: {{question_count}} câu. Yêu cầu trả về kết quả dưới định dạng {{output_format}} và đảm bảo các công thức toán học được trình bày chuẩn theo dạng {{math_format}}.")
                .allowedVariables("grade_level,subject,topic,difficulty,question_count,output_format,math_format")
                .description("Tự động tạo bộ câu hỏi trắc nghiệm và tự luận môn Toán.")
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
