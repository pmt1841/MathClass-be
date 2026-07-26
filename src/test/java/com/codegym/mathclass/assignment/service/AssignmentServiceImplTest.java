package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.mapper.AssignmentMapper;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetItemRepository;
import com.codegym.mathclass.assignment.service.impl.AssignmentServiceImpl;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Mock
    private AssignmentSheetItemRepository assignmentSheetItemRepository;

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

        publishRequest = new PublishAssignmentRequest();
        publishRequest.setTargets(
                List.of(new PublishAssignmentRequest.TargetClass("MATH2024", LocalDateTime.now().plusDays(7))));

        draftAssignment = new Assignment();
        draftAssignment.setId(assignmentId);
        draftAssignment.setTitle("Bài tập tích phân");
        draftAssignment.setDescription("Tính $\\int_0^1 x^2 dx$");
        draftAssignment.setContent("Nội dung bài tập");
        draftAssignment.setDeadline(null);
        draftAssignment.setStatus(AssignmentStatus.DRAFT);
        draftAssignment.setTeacher(teacher);
        draftAssignment.setClassroom(null);
    }

    @Nested
    @DisplayName("createAssignment Tests (Bước 1 – Tạo DRAFT)")
    class CreateAssignmentTests {

        @Test
        @DisplayName("Should create assignment as DRAFT successfully when user is a teacher")
        void createAssignment_UserIsTeacher_ReturnsAssignmentResponse() {
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

            AssignmentResponse response = assignmentService.createAssignment(createRequest, teacherId);

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
            when(userRepository.findById(teacherId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Không tìm thấy người dùng");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw RuntimeException when user role is STUDENT, not TEACHER")
        void createAssignment_UserNotTeacher_ThrowsException() {
            when(userRepository.findById(teacherId)).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Chỉ giáo viên mới có quyền tạo bài tập");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when description contains dangerous LaTeX command \\input")
        void createAssignment_DangerousLaTeXInput_ThrowsException() {
            createRequest.setContent("Xem file này: \\input{/etc/passwd}");
            when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

            assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("\\input");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when description contains dangerous LaTeX command \\write")
        void createAssignment_DangerousLaTeXWrite_ThrowsException() {
            createRequest.setContent("\\write18{rm -rf /}");
            when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

            assertThatThrownBy(() -> assignmentService.createAssignment(createRequest, teacherId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lệnh LaTeX không được phép");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow safe LaTeX math expressions in description")
        void createAssignment_SafeLaTeX_Success() {
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

            AssignmentResponse response = assignmentService.createAssignment(createRequest, teacherId);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(AssignmentStatus.DRAFT);

            verify(assignmentRepository, times(1)).save(any(Assignment.class));
        }
    }

    @Nested
    @DisplayName("publishAssignment Tests (Bước 2 – Publish)")
    class PublishAssignmentTests {

        @Test
        @DisplayName("Should publish assignment successfully and assign classrooms")
        void publishAssignment_ValidRequest_Success() {
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(classroom));

            assignmentService.publishAssignment(assignmentId, publishRequest, teacherId);

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
            assertThat(savedClones).hasSize(2);
            assertThat(savedClones).anyMatch(c -> c.getClassroom().getClassCode().equals("MATH2024"));
            assertThat(savedClones).anyMatch(c -> c.getClassroom().getClassCode().equals("MATH2025"));
        }

        @Test
        @DisplayName("Should throw RuntimeException when assignment is not found")
        void publishAssignment_AssignmentNotFound_ThrowsException() {
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Không tìm thấy bài tập");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw RuntimeException when teacher does not own the assignment")
        void publishAssignment_NotOwner_ThrowsException() {
            long otherTeacherId = 999L;
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

            assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, otherTeacherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Bạn không có quyền publish bài tập này");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when assignment is DELETED")
        void publishAssignment_Deleted_ThrowsException() {
            draftAssignment.setStatus(AssignmentStatus.DELETED);

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

            assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bài tập đã bị xóa không thể giao bài");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw RuntimeException when classroom does not belong to the teacher")
        void publishAssignment_ClassNotBelongToTeacher_ThrowsException() {
            User otherTeacher = new User();
            otherTeacher.setId(888L);
            otherTeacher.setRole(Role.TEACHER);

            Classroom otherClassroom = new Classroom();
            otherClassroom.setId(200L);
            otherClassroom.setClassCode("MATH2024");
            otherClassroom.setTeacher(otherTeacher);

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.of(otherClassroom));

            assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("MATH2024")
                    .hasMessageContaining("không có quyền");

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw RuntimeException when one of the classCodes is not found")
        void publishAssignment_ClassroomNotFound_ThrowsException() {
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(classroomRepository.findByClassCode("MATH2024")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentService.publishAssignment(assignmentId, publishRequest, teacherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("MATH2024");

            verify(assignmentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAssignmentById Tests (Security Hardening)")
    class GetAssignmentByIdTests {

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
            classObj.setStudents(new HashSet<>(List.of(student)));
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
            classObj.setStudents(new HashSet<>());
            assignment.setClassroom(classObj);

            when(assignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));

            assertThatThrownBy(() -> assignmentService.getAssignmentById(100L, student.getId(), "STUDENT"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền xem bài tập này");
        }
    }

    @Nested
    @DisplayName("deleteAssignment Tests")
    class DeleteAssignmentTests {

        @Test
        @DisplayName("Should delete DRAFT assignment hard from repository when no submissions exist")
        void deleteAssignment_DraftNoSubmissions_HardDeletes() {
            draftAssignment.setStatus(AssignmentStatus.DRAFT);
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(submissionRepository.existsByAssignmentId(assignmentId)).thenReturn(false);
            when(assignmentSheetItemRepository.findByAssignmentIdIn(any())).thenReturn(List.of());

            assignmentService.deleteAssignment(assignmentId, teacherId);

            verify(assignmentRepository, times(1)).delete(draftAssignment);
        }

        @Test
        @DisplayName("Should delete PUBLISHED assignment soft by setting status to DELETED")
        void deleteAssignment_PublishedNoSubmissions_SoftDeletes() {
            draftAssignment.setStatus(AssignmentStatus.PUBLISHED);
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(submissionRepository.existsByAssignmentId(assignmentId)).thenReturn(false);
            when(assignmentSheetItemRepository.findByAssignmentIdIn(any())).thenReturn(List.of());

            assignmentService.deleteAssignment(assignmentId, teacherId);

            assertThat(draftAssignment.getStatus()).isEqualTo(AssignmentStatus.DELETED);
            verify(assignmentRepository, times(1)).save(draftAssignment);
        }

        @Test
        @DisplayName("Should throw BadRequestException when deleting assignment with student submissions")
        void deleteAssignment_HasSubmissions_ThrowsBadRequestException() {
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(submissionRepository.existsByAssignmentId(assignmentId)).thenReturn(true);

            assertThatThrownBy(() -> assignmentService.deleteAssignment(assignmentId, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Đã có học sinh nộp bài, không thể xóa bài tập này");

            verify(assignmentRepository, never()).delete(any(Assignment.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-owner teacher tries to delete assignment")
        void deleteAssignment_NotOwner_ThrowsAccessDeniedException() {
            long nonOwnerTeacherId = 999L;
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

            assertThatThrownBy(() -> assignmentService.deleteAssignment(assignmentId, nonOwnerTeacherId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền xóa bài tập này");
        }
    }

    @Nested
    @DisplayName("updateAssignment Tests")
    class UpdateAssignmentTests {

        @Test
        @DisplayName("Should update DRAFT assignment successfully")
        void updateAssignment_Draft_Success() {
            draftAssignment.setStatus(AssignmentStatus.DRAFT);
            UpdateAssignmentRequest updateRequest = new UpdateAssignmentRequest();
            updateRequest.setTitle("Tiêu đề mới");
            updateRequest.setDescription("Mô tả mới");
            updateRequest.setContent("Nội dung mới $x+1$");

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));
            when(assignmentRepository.save(draftAssignment)).thenReturn(draftAssignment);
            AssignmentResponse mockResponse = new AssignmentResponse();
            mockResponse.setTitle("Tiêu đề mới");
            when(assignmentMapper.toAssignmentResponse(draftAssignment)).thenReturn(mockResponse);

            AssignmentResponse response = assignmentService.updateAssignment(assignmentId, updateRequest, teacherId);

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Tiêu đề mới");
            verify(assignmentRepository, times(1)).save(draftAssignment);
        }

        @Test
        @DisplayName("Should throw BadRequestException when updating DELETED assignment")
        void updateAssignment_Deleted_ThrowsBadRequestException() {
            draftAssignment.setStatus(AssignmentStatus.DELETED);
            UpdateAssignmentRequest updateRequest = new UpdateAssignmentRequest();

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(draftAssignment));

            assertThatThrownBy(() -> assignmentService.updateAssignment(assignmentId, updateRequest, teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không thể sửa bài tập đã bị xóa");
        }
    }
}
