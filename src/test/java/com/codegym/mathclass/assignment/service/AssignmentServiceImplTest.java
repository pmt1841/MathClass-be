package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.service.impl.AssignmentServiceImpl;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.utils.SupabaseStorageService;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private SubmissionRepository submissionRepository;

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
        draftAssignment.setContent("Nội dung bài tập");
        draftAssignment.setDeadline(null); // deadline = null khi còn là DRAFT
        draftAssignment.setStatus(AssignmentStatus.DRAFT);
        draftAssignment.setTeacher(teacher);
        draftAssignment.setClassroom(null);
    }

    // =====================================================
    // Tests cho createAssignment (Bước 1 – Tạo DRAFT)
    // =====================================================

    @Test
    @DisplayName("Should create assignment as DRAFT successfully when user is a teacher")
    void createAssignment_UserIsTeacher_ReturnsAssignmentResponse() {
        // Given
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(assignmentId);
            return a;
        });
        
        AssignmentResponse mockResponse = new AssignmentResponse();
        mockResponse.setId(assignmentId);
        mockResponse.setTitle("Bài tập tích phân");
        mockResponse.setStatus(AssignmentStatus.DRAFT);
        mockResponse.setTeacherId(teacherId);
        when(assignmentMapper.toAssignmentResponse(any(Assignment.class))).thenReturn(mockResponse);

        // When
        AssignmentResponse response = assignmentService.createAssignment(createRequest, teacherId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(assignmentId);
        assertThat(response.getTitle()).isEqualTo("Bài tập tích phân");
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.DRAFT);
        assertThat(response.isOpen()).isFalse();
        assertThat(response.getDeadline()).isNull();
        assertThat(response.getClassCode()).isNull();
        assertThat(response.getTeacherId()).isEqualTo(teacherId);

        verify(userRepository, times(1)).findById(teacherId);
        verify(assignmentRepository, times(1)).save(any(Assignment.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when teacher is not found")
    void createAssignment_TeacherNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(teacherId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy người dùng");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when user role is STUDENT, not TEACHER")
    void createAssignment_UserNotTeacher_ThrowsException() {
        // Given
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(student));

        // When & Then
        assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Chỉ giáo viên mới có quyền tạo bài tập");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when description contains dangerous LaTeX command \\input")
    void createAssignment_DangerousLaTeXInput_ThrowsException() {
        // Given
        createRequest.setContent("Xem file này: \\input{/etc/passwd}");
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        // When & Then
        assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\\input");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when description contains dangerous LaTeX command \\write")
    void createAssignment_DangerousLaTeXWrite_ThrowsException() {
        // Given
        createRequest.setContent("\\write18{rm -rf /}");
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        // When & Then
        assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lệnh LaTeX không được phép");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow safe LaTeX math expressions in description")
    void createAssignment_SafeLaTeX_Success() {
        // Given
        createRequest.setDescription("Giải phương trình $ax^2 + bx + c = 0$, " +
                "sử dụng công thức $x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}$");

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(assignmentId);
            return a;
        });

        AssignmentResponse mockResponse = new AssignmentResponse();
        mockResponse.setStatus(AssignmentStatus.DRAFT);
        when(assignmentMapper.toAssignmentResponse(any(Assignment.class))).thenReturn(mockResponse);

        // When
        AssignmentResponse response = assignmentService.createAssignment(createRequest, teacherId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.DRAFT);
        
        verify(assignmentRepository, times(1)).save(any(Assignment.class));
    }

    // =====================================================
    // Tests cho publishAssignment (Bước 2 – Publish)
    // =====================================================

    @Test
    @DisplayName("Should publish assignment successfully and assign classrooms")
    void publishAssignment_ValidRequest_Success() {
        // Given
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(classroom));

        // When
        assignmentService.publishAssignment(assignmentId, publishRequest, teacherId);

        // Then
        assertThat(draftAssignment.getStatus()).isEqualTo(AssignmentStatus.ARCHIVED);
        verify(assignmentRepository, times(1)).save(draftAssignment);

        org.mockito.ArgumentCaptor<List<Assignment>> listCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository, times(1)).saveAll(listCaptor.capture());

        List<Assignment> savedClones = listCaptor.getValue();
        assertThat(savedClones).hasSize(1);
        
        Assignment clone = savedClones.get(0);
        assertThat(clone.getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);
        assertThat(clone.getClassroom().getClassCode()).isEqualTo("MATH2024");
        assertThat(clone.getDeadline()).isNotNull();
    }

    @Test
    @DisplayName("Should publish to multiple classrooms successfully")
    void publishAssignment_MultipleClasses_Success() {
        // Given
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

        // When
        assignmentService.publishAssignment(assignmentId, publishRequest, teacherId);

        // Then
        org.mockito.ArgumentCaptor<List<Assignment>> listCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository, times(1)).saveAll(listCaptor.capture());

        List<Assignment> savedClones = listCaptor.getValue();
        assertThat(savedClones).hasSize(2);
        assertThat(savedClones).anyMatch(c -> c.getClassroom().getClassCode().equals("MATH2024"));
        assertThat(savedClones).anyMatch(c -> c.getClassroom().getClassCode().equals("MATH2025"));
    }

    @Test
    @DisplayName("Should throw RuntimeException when assignment is not found")
    void publishAssignment_AssignmentNotFound_ThrowsException() {
        // Given
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Không tìm thấy bài tập");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when teacher does not own the assignment")
    void publishAssignment_NotOwner_ThrowsException() {
        // Given
        long otherTeacherId = 999L;
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

        // When & Then
        assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, otherTeacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bạn không có quyền publish bài tập này");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when assignment is already published")
    void publishAssignment_AlreadyPublished_ThrowsException() {
        // Given
        draftAssignment.setStatus(AssignmentStatus.PUBLISHED);
        draftAssignment.setClassroom(classroom);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

        // When & Then
        assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bài tập đã được publish hoặc archive trước đó");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when classroom does not belong to the teacher")
    void publishAssignment_ClassNotBelongToTeacher_ThrowsException() {
        // Given
        User otherTeacher = new User();
        otherTeacher.setId(888L);
        otherTeacher.setRole(Role.TEACHER);

        Classroom otherClassroom = new Classroom();
        otherClassroom.setId(200L);
        otherClassroom.setClassCode("MATH2024");
        otherClassroom.setTeacher(otherTeacher); // Lớp của giáo viên khác

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(otherClassroom));

        // When & Then
        assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MATH2024")
                .hasMessageContaining("không có quyền");
                
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when one of the classCodes is not found")
    void publishAssignment_ClassroomNotFound_ThrowsException() {
        // Given
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
        when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MATH2024");
                
        verify(assignmentRepository, never()).save(any());
    }

    // ==========================================
    // Tests for getAssignmentById (Security Hardening)
    // ==========================================

    @Test
    @DisplayName("Teacher should get their own assignment successfully")
    void getAssignmentById_TeacherOwnAssignment_ReturnsResponse() {
        Assignment assignment = new Assignment();
        assignment.setId(100L);
        assignment.setTeacher(teacher);

        AssignmentResponse mockResponse = new AssignmentResponse();
        mockResponse.setId(100L);

        when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));
        when(assignmentMapper.toAssignmentResponse(assignment)).thenReturn(mockResponse);

        AssignmentResponse response = assignmentService.getAssignmentById(100L, teacherId, "TEACHER");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Teacher should not get another teacher's assignment")
    void getAssignmentById_TeacherNotOwnAssignment_ThrowsAccessDeniedException() {
        Assignment assignment = new Assignment();
        assignment.setId(100L);
        User otherTeacher = new User();
        otherTeacher.setId(999L);
        assignment.setTeacher(otherTeacher);

        when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> assignmentService.getAssignmentById(100L, teacherId, "TEACHER"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền xem bài tập này");
    }

    @Test
    @DisplayName("Student in class should get published assignment successfully")
    void getAssignmentById_StudentInClassPublishedAssignment_ReturnsResponse() {
        Assignment assignment = new Assignment();
        assignment.setId(100L);
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        
        Classroom classObj = new Classroom();
        classObj.setId(200L);
        classObj.setStudents(new java.util.HashSet<>(java.util.List.of(student)));
        assignment.setClassroom(classObj);

        AssignmentResponse mockResponse = new AssignmentResponse();
        mockResponse.setId(100L);

        when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));
        when(assignmentMapper.toAssignmentResponse(assignment)).thenReturn(mockResponse);

        AssignmentResponse response = assignmentService.getAssignmentById(100L, student.getId(), "STUDENT");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Student not in class should fail to get published assignment")
    void getAssignmentById_StudentNotInClassPublishedAssignment_ThrowsAccessDeniedException() {
        Assignment assignment = new Assignment();
        assignment.setId(100L);
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        
        Classroom classObj = new Classroom();
        classObj.setId(200L);
        classObj.setStudents(new java.util.HashSet<>()); // Không có học sinh này
        assignment.setClassroom(classObj);

        when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> assignmentService.getAssignmentById(100L, student.getId(), "STUDENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền xem bài tập này");
    }
}
