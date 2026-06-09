package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    private User teacher;
    private User student;
    private Classroom classroom;
    private CreateAssignmentRequest createRequest;
    private PublishAssignmentRequest publishRequest;
    private Assignment draftAssignment;
    private final long teacherId = 1L;
    private final long assignmentId = 10L;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(teacherId);
        teacher.setEmail("teacher@codegym.com");
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setRole(Role.TEACHER);
        teacher.setActive(true);

        student = new User();
        student.setId(2L);
        student.setEmail("student@codegym.com");
        student.setFullName("Tran Thi Student");
        student.setRole(Role.STUDENT);
        student.setActive(true);

        classroom = new Classroom();
        classroom.setId(100L);
        classroom.setClassCode("MATH2024");
        classroom.setClassName("Toán Cao Cấp");
        classroom.setTeacher(teacher);
        classroom.setStudents(new HashSet<>());

        createRequest = new CreateAssignmentRequest();
        createRequest.setTitle("Bài tập tích phân");
        createRequest.setDescription("Tính $\\int_0^1 x^2 dx$");
        // Không có deadline khi tạo DRAFT

        publishRequest = new PublishAssignmentRequest();
        publishRequest.setTargets(
                List.of(new PublishAssignmentRequest.TargetClass("MATH2024", LocalDateTime.now().plusDays(7))));

        draftAssignment = new Assignment();
        draftAssignment.setId(assignmentId);
        draftAssignment.setTitle("Bài tập tích phân");
        draftAssignment.setDescription("Tính $\\int_0^1 x^2 dx$");
        draftAssignment.setDeadline(null); // deadline = null khi còn là DRAFT
        draftAssignment.setStatus(AssignmentStatus.DRAFT);
        draftAssignment.setTeacher(teacher);
        draftAssignment.setClassroom(null);
    }

    // =====================================================
    // Tests cho createAssignment (Bước 1 – Tạo DRAFT)
    // =====================================================

    @Test
    @DisplayName("should create assignment as DRAFT successfully when user is a teacher")
    void createAssignment_Success() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(assignmentId);
            return a;
        });

        AssignmentResponse response = assignmentService.createAssignment(createRequest, teacherId);

        assertNotNull(response);
        assertEquals(assignmentId, response.getId());
        assertEquals("Bài tập tích phân", response.getTitle());
        assertEquals(AssignmentStatus.DRAFT, response.getStatus());
        // DRAFT → chưa có deadline, isOpen luôn là false
        assertFalse(response.isOpen());
        assertNull(response.getDeadline());
        assertNull(response.getClassCode());
        assertEquals(teacherId, response.getTeacherId());

        verify(userRepository, times(1)).findById(teacherId);
        verify(assignmentRepository, times(1)).save(any(Assignment.class));
    }

    @Test
    @DisplayName("should throw RuntimeException when teacher is not found")
    void createAssignment_TeacherNotFound_ThrowsException() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.createAssignment(createRequest, teacherId));

        assertEquals("Không tìm thấy người dùng", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw RuntimeException when user role is STUDENT, not TEACHER")
    void createAssignment_UserNotTeacher_ThrowsException() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(student));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.createAssignment(createRequest, teacherId));

        assertEquals("Chỉ giáo viên mới có quyền tạo bài tập", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when description contains dangerous LaTeX command \\input")
    void createAssignment_DangerousLaTeX_input_ThrowsException() {
        createRequest.setDescription("Xem file này: \\input{/etc/passwd}");

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assignmentService.createAssignment(createRequest, teacherId));

        assertTrue(ex.getMessage().contains("\\input"),
                "Message lỗi phải chứa tên lệnh nguy hiểm");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when description contains dangerous LaTeX command \\write")
    void createAssignment_DangerousLaTeX_write_ThrowsException() {
        createRequest.setDescription("\\write18{rm -rf /}");

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assignmentService.createAssignment(createRequest, teacherId));

        assertTrue(ex.getMessage().contains("lệnh LaTeX không được phép"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should allow safe LaTeX math expressions in description")
    void createAssignment_SafeLaTeX_Success() {
        // LaTeX toán học thông thường – phải được chấp nhận
        createRequest.setDescription("Giải phương trình $ax^2 + bx + c = 0$, " +
                "sử dụng công thức $x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}$");

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(assignmentId);
            return a;
        });

        AssignmentResponse response = assignmentService.createAssignment(createRequest, teacherId);

        assertNotNull(response);
        assertEquals(AssignmentStatus.DRAFT, response.getStatus());
        verify(assignmentRepository, times(1)).save(any(Assignment.class));
    }

    // =====================================================
    // Tests cho publishAssignment (Bước 2 – Publish)
    // =====================================================

    @Test
    @DisplayName("should publish assignment successfully and assign classrooms")
    void publishAssignment_Success() {
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(classroom));

        assignmentService.publishAssignment(assignmentId, publishRequest, teacherId);

        assertEquals(AssignmentStatus.ARCHIVED, draftAssignment.getStatus());
        verify(assignmentRepository, times(1)).save(draftAssignment);

        org.mockito.ArgumentCaptor<List<Assignment>> listCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository, times(1)).saveAll(listCaptor.capture());

        List<Assignment> savedClones = listCaptor.getValue();
        assertEquals(1, savedClones.size());
        Assignment clone = savedClones.get(0);
        assertEquals(AssignmentStatus.PUBLISHED, clone.getStatus());
        assertEquals("MATH2024", clone.getClassroom().getClassCode());
        assertNotNull(clone.getDeadline(), "Deadline phải được gán sau khi publish");
    }

    @Test
    @DisplayName("should publish to multiple classrooms successfully")
    void publishAssignment_MultipleClasses_Success() {
        Classroom classroom2 = new Classroom();
        classroom2.setId(101L);
        classroom2.setClassCode("MATH2025");
        classroom2.setClassName("Giải Tích");
        classroom2.setTeacher(teacher);
        classroom2.setStudents(new HashSet<>());

        PublishAssignmentRequest.TargetClass target1 = new PublishAssignmentRequest.TargetClass("MATH2024",
                LocalDateTime.now().plusDays(7));
        PublishAssignmentRequest.TargetClass target2 = new PublishAssignmentRequest.TargetClass("MATH2025",
                LocalDateTime.now().plusDays(7));
        publishRequest.setTargets(List.of(target1, target2));

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(classroom));
        when(classroomRepository.findByClassCode("MATH2025")).thenReturn(Optional.of(classroom2));

        assignmentService.publishAssignment(assignmentId, publishRequest, teacherId);

        org.mockito.ArgumentCaptor<List<Assignment>> listCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository, times(1)).saveAll(listCaptor.capture());

        List<Assignment> savedClones = listCaptor.getValue();
        assertEquals(2, savedClones.size());
        assertTrue(savedClones.stream().anyMatch(c -> c.getClassroom().getClassCode().equals("MATH2024")));
        assertTrue(savedClones.stream().anyMatch(c -> c.getClassroom().getClassCode().equals("MATH2025")));
    }

    @Test
    @DisplayName("should throw RuntimeException when assignment is not found")
    void publishAssignment_AssignmentNotFound_ThrowsException() {
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId));

        assertEquals("Không tìm thấy bài tập", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw RuntimeException when teacher does not own the assignment")
    void publishAssignment_NotOwner_ThrowsException() {
        long otherTeacherId = 999L;
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.publishAssignment(assignmentId, publishRequest, otherTeacherId));

        assertEquals("Bạn không có quyền publish bài tập này", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw RuntimeException when assignment is already published")
    void publishAssignment_AlreadyPublished_ThrowsException() {
        draftAssignment.setStatus(AssignmentStatus.PUBLISHED);
        draftAssignment.setClassroom(classroom);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId));

        assertEquals("Bài tập đã được publish hoặc archive trước đó", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw RuntimeException when classroom does not belong to the teacher")
    void publishAssignment_ClassNotBelongToTeacher_ThrowsException() {
        User otherTeacher = new User();
        otherTeacher.setId(888L);
        otherTeacher.setRole(Role.TEACHER);

        Classroom otherClassroom = new Classroom();
        otherClassroom.setId(200L);
        otherClassroom.setClassCode("MATH2024");
        otherClassroom.setTeacher(otherTeacher); // Lớp của giáo viên khác

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(otherClassroom));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId));

        assertTrue(ex.getMessage().contains("MATH2024"));
        assertTrue(ex.getMessage().contains("không có quyền"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw RuntimeException when one of the classCodes is not found")
    void publishAssignment_ClassroomNotFound_ThrowsException() {
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId));

        assertTrue(ex.getMessage().contains("MATH2024"));
        verify(assignmentRepository, never()).save(any());
    }
}
