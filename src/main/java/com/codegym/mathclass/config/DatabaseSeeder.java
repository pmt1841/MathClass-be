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
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.entity.Permission;
import com.codegym.mathclass.user.entity.RolePermission;
import com.codegym.mathclass.user.repository.PermissionRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.repository.UserRepository;
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

    @Value("${mathclass.seed.enabled:true}")
    private boolean isSeedEnabled;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!isSeedEnabled) {
            log.info("[DatabaseSeeder] Database seeding is disabled.");
            return;
        }

        if (permissionRepository.count() == 0) {
            log.info("[DatabaseSeeder] Permissions are empty. Seeding permissions...");
            seedPermissions();
        }

        if (userRepository.count() > 0) {
            log.info("[DatabaseSeeder] Users exist in database. Skipping data seeding.");
            return;
        }

        log.info("[DatabaseSeeder] Database is empty. Seeding sample data...");

        try {
            seedData();
            log.info("[DatabaseSeeder] Database seeded successfully.");
        } catch (Exception e) {
            log.error("[DatabaseSeeder] Error during database seeding!", e);
            throw e;
        }
    }

    private void seedPermissions() {
        // 0. Create Permissions and RolePermissions
        log.info("[DatabaseSeeder] Creating permissions...");
        // Classroom permissions
        Permission classCreate = permissionRepository.save(Permission.builder().name("classroom:create").description("Tạo lớp học").build());
        Permission classUpdate = permissionRepository.save(Permission.builder().name("classroom:update").description("Sửa lớp học").build());
        Permission classDelete = permissionRepository.save(Permission.builder().name("classroom:delete").description("Xóa lớp học").build());
        Permission classManageReq = permissionRepository.save(Permission.builder().name("classroom:manage_requests").description("Quản lý yêu cầu tham gia").build());
        Permission classRemoveStu = permissionRepository.save(Permission.builder().name("classroom:remove_student").description("Xóa học sinh").build());
        Permission classJoin = permissionRepository.save(Permission.builder().name("classroom:join").description("Tham gia lớp").build());
        Permission classJoinStatus = permissionRepository.save(Permission.builder().name("classroom:join_status").description("Xem trạng thái tham gia").build());

        // Assignment permissions
        Permission assignCreate = permissionRepository.save(Permission.builder().name("assignment:create").description("Tạo bài tập").build());
        Permission assignUpdate = permissionRepository.save(Permission.builder().name("assignment:update").description("Sửa bài tập").build());
        Permission assignDelete = permissionRepository.save(Permission.builder().name("assignment:delete").description("Xóa bài tập").build());
        Permission assignPublish = permissionRepository.save(Permission.builder().name("assignment:publish").description("Xuất bản bài tập").build());
        Permission assignRead = permissionRepository.save(Permission.builder().name("assignment:read").description("Xem bài tập").build());

        // Submission permissions
        Permission subSubmit = permissionRepository.save(Permission.builder().name("submission:submit").description("Nộp bài").build());
        Permission subReadOwn = permissionRepository.save(Permission.builder().name("submission:read_own").description("Xem bài nộp của mình").build());
        Permission subGrade = permissionRepository.save(Permission.builder().name("submission:grade").description("Chấm điểm").build());
        Permission subReadAll = permissionRepository.save(Permission.builder().name("submission:read_all").description("Xem tất cả bài nộp").build());
        Permission subComment = permissionRepository.save(Permission.builder().name("submission:comment").description("Bình luận bài nộp").build());

        // Dashboard permissions
        Permission dashTeacher = permissionRepository.save(Permission.builder().name("dashboard:teacher_view").description("Xem thống kê giáo viên").build());
        Permission dashStudent = permissionRepository.save(Permission.builder().name("dashboard:student_view").description("Xem thống kê học sinh").build());

        // Admin permissions
        Permission manageUsers = permissionRepository.save(Permission.builder().name("user:manage").description("Quản lý người dùng").build());

        log.info("[DatabaseSeeder] Assigning permissions to roles...");
        // ADMIN
        rolePermissionRepository.save(RolePermission.builder().role(Role.ADMIN).permission(manageUsers).build());
        
        // TEACHER
        List<Permission> teacherPerms = List.of(
                classCreate, classUpdate, classDelete, classManageReq, classRemoveStu,
                assignCreate, assignUpdate, assignDelete, assignPublish, assignRead,
                subGrade, subReadAll, subComment, dashTeacher
        );
        teacherPerms.forEach(p -> rolePermissionRepository.save(RolePermission.builder().role(Role.TEACHER).permission(p).build()));
        
        // STUDENT
        List<Permission> studentPerms = List.of(
                classJoin, classJoinStatus, assignRead, subSubmit, subReadOwn, subComment, dashStudent
        );
        studentPerms.forEach(p -> rolePermissionRepository.save(RolePermission.builder().role(Role.STUDENT).permission(p).build()));
    }

    private void seedData() {
        // 1. Create Users (Admin, Teachers, Students)
        log.info("[DatabaseSeeder] Creating sample users...");
        User admin = createUser("admin@mathclass.com", "Admin Hệ Thống", "password123", "0901234567", Role.ADMIN, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=admin");
        User teacher1 = createUser("teacher1@mathclass.com", "Thầy Nguyễn Văn A", "password123", "0902234567", Role.TEACHER, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=teacher1");
        User teacher2 = createUser("teacher2@mathclass.com", "Cô Trần Thị B", "password123", "0903234567", Role.TEACHER, Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=teacher2");

        User student1 = createUser("student1@mathclass.com", "Học sinh Nguyễn Văn An", "password123", "0904234567", Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student1");
        User student2 = createUser("student2@mathclass.com", "Học sinh Lê Thị Bình", "password123", "0905234567", Role.STUDENT, Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student2");
        User student3 = createUser("student3@mathclass.com", "Học sinh Phạm Văn Cường", "password123", "0906234567", Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student3");
        User student4 = createUser("student4@mathclass.com", "Học sinh Hoàng Thị Dung", "password123", "0907234567", Role.STUDENT, Gender.FEMALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student4");
        User student5 = createUser("student5@mathclass.com", "Học sinh Vũ Văn Em", "password123", "0908234567", Role.STUDENT, Gender.MALE, "https://api.dicebear.com/7.x/adventurer/svg?seed=student5");

        // 2. Create Classrooms
        log.info("[DatabaseSeeder] Creating classrooms...");
        Classroom class1 = new Classroom();
        class1.setClassCode("MATH101");
        class1.setClassName("Lớp Toán Đại số 10");
        class1.setTeacher(teacher1);
        class1.setMaxStudents(30);
        class1.setDescription("Lớp toán đại số cơ bản dành cho học sinh lớp 10 năm học 2026-2027.");
        class1.setStudents(new HashSet<>(Arrays.asList(student1, student2, student3)));
        class1 = classroomRepository.save(class1);

        Classroom class2 = new Classroom();
        class2.setClassCode("MATH102");
        class2.setClassName("Lớp Toán Hình học 11");
        class2.setTeacher(teacher2);
        class2.setMaxStudents(25);
        class2.setDescription("Lớp học chuyên đề hình học không gian và phương pháp tọa độ lớp 11.");
        class2.setStudents(new HashSet<>(Arrays.asList(student3, student4, student5)));
        class2 = classroomRepository.save(class2);

        // 3. Create Classroom Join Requests
        log.info("[DatabaseSeeder] Creating join requests...");
        ClassroomJoinRequest req1 = new ClassroomJoinRequest();
        req1.setClassroom(class1);
        req1.setStudent(student4);
        req1.setStatus(JoinRequestStatus.PENDING);
        classroomJoinRequestRepository.save(req1);

        ClassroomJoinRequest req2 = new ClassroomJoinRequest();
        req2.setClassroom(class1);
        req2.setStudent(student5);
        req2.setStatus(JoinRequestStatus.REJECTED);
        classroomJoinRequestRepository.save(req2);

        // 4. Create Assignments
        log.info("[DatabaseSeeder] Creating assignments...");
        Assignment assign1 = new Assignment();
        assign1.setTitle("Bài tập hàm số bậc hai");
        assign1.setDescription("Các em hoàn thành các bài tập sau về hàm số bậc hai $y = ax^2 + bx + c$. Yêu cầu vẽ đồ thị phụ họa.");
        assign1.setContent("1. Khảo sát sự biến thiên và vẽ đồ thị hàm số $y = x^2 - 4x + 3$.\n2. Tìm m để phương trình $x^2 - 2(m+1)x + m^2 + 2 = 0$ có hai nghiệm phân biệt.");
        assign1.setTeacher(teacher1);
        assign1.setClassroom(class1);
        assign1.setStatus(AssignmentStatus.PUBLISHED);
        assign1.setDeadline(LocalDateTime.now().plusDays(7));
        assign1 = assignmentRepository.save(assign1);

        // Add drawing to Assignment 1
        AssignmentDrawing draw1 = new AssignmentDrawing();
        draw1.setAssignment(assign1);
        draw1.setShapeCode("PARABOLA_01");
        Map<String, Object> graphData = new HashMap<>();
        graphData.put("type", "parabola");
        graphData.put("a", 1.0);
        graphData.put("b", -4.0);
        graphData.put("c", 3.0);
        draw1.setJsxGraphData(graphData);
        assignmentDrawingRepository.save(draw1);

        Assignment assign2 = new Assignment();
        assign2.setTitle("Bài tập vectơ trong không gian");
        assign2.setDescription("Học sinh thực hiện vẽ hình biểu diễn và tính tích vô hướng của hai vectơ $\\vec{u}$ và $\\vec{v}$ trong không gian Oxyz.");
        assign2.setContent("Cho hình chóp S.ABCD có đáy ABCD là hình vuông cạnh a. Cạnh bên SA vuông góc với mặt phẳng đáy và SA = a. Tính góc giữa đường thẳng SD và mặt phẳng (SBC).");
        assign2.setTeacher(teacher2);
        assign2.setClassroom(class2);
        assign2.setStatus(AssignmentStatus.PUBLISHED);
        assign2.setDeadline(LocalDateTime.now().plusDays(5));
        assign2 = assignmentRepository.save(assign2);

        Assignment assign3 = new Assignment();
        assign3.setTitle("Bài tập trắc nghiệm số học (DRAFT)");
        assign3.setDescription("Bài tập kiểm tra kiến thức về ước chung lớn nhất và bội chung nhỏ nhất.");
        assign3.setContent("Câu 1: Tìm UCLN(24, 36).\nCâu 2: Số nguyên tố là gì?");
        assign3.setTeacher(teacher1);
        assign3.setClassroom(class1);
        assign3.setStatus(AssignmentStatus.DRAFT);
        assign3.setDeadline(null);
        assignmentRepository.save(assign3);

        // 5. Create Submissions
        log.info("[DatabaseSeeder] Creating submissions...");
        // Submission 1: Graded
        Submission sub1 = new Submission();
        sub1.setAssignment(assign1);
        sub1.setStudent(student1);
        sub1.setContent("Em xin gửi bài làm hàm số bậc hai: \n1. Hàm số $y = x^2 - 4x + 3$ có tọa độ đỉnh $I(2, -1)$, cắt Oy tại $(0, 3)$, cắt Ox tại $(1, 0)$ và $(3, 0)$.");
        sub1.setStatus(SubmissionStatus.GRADED);
        sub1.setScore(9.0);
        sub1.setSubmittedAt(LocalDateTime.now().minusDays(1));
        sub1.setTeacherFeedback("Bài làm tốt, trình bày sạch sẽ và giải toán chính xác.");
        sub1 = submissionRepository.save(sub1);

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
        Submission sub2 = new Submission();
        sub2.setAssignment(assign1);
        sub2.setStudent(student2);
        sub2.setContent("Bài làm của em Lê Thị Bình: \nCâu 1: Hàm số có bảng biến thiên đi xuống từ $-\\infty$ đến $2$ và đi lên từ $2$ đến $+\\infty$.");
        sub2.setStatus(SubmissionStatus.SUBMITTED);
        sub2.setSubmittedAt(LocalDateTime.now().minusHours(2));
        submissionRepository.save(sub2);

        // Submission 3: Graded
        Submission sub3 = new Submission();
        sub3.setAssignment(assign2);
        sub3.setStudent(student3);
        sub3.setContent("Lời giải hình học không gian S.ABCD: \nGóc giữa SD và (SBC) bằng góc $\\widehat{DSE}$ với E là hình chiếu của D lên SB...");
        sub3.setStatus(SubmissionStatus.GRADED);
        sub3.setScore(8.5);
        sub3.setSubmittedAt(LocalDateTime.now().minusDays(2));
        sub3.setTeacherFeedback("Phân tích góc tốt, tính toán chính xác.");
        submissionRepository.save(sub3);

        // 6. Create Notifications
        log.info("[DatabaseSeeder] Creating notifications...");
        Notification notif1 = Notification.builder()
                .user(student1)
                .message("Bài tập 'Bài tập hàm số bậc hai' của bạn đã được chấm điểm: 9.0")
                .link("/student/assignments/" + assign1.getId())
                .isRead(false)
                .build();
        notificationRepository.save(notif1);

        Notification notif2 = Notification.builder()
                .user(teacher1)
                .message("Học sinh Lê Thị Bình đã nộp bài tập 'Bài tập hàm số bậc hai'")
                .link("/teacher/submissions/" + sub2.getId())
                .isRead(false)
                .build();
        notificationRepository.save(notif2);
    }

    private User createUser(String email, String fullName, String password, String phone, Role role, Gender gender, String avatarUrl) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhoneNumber(phone);
        user.setRole(role);
        user.setActive(true);
        user.setDateOfBirth(LocalDate.of(1995, 1, 1));
        user.setGender(gender);
        user.setAvatarUrl(avatarUrl);
        user = userRepository.save(user);

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
