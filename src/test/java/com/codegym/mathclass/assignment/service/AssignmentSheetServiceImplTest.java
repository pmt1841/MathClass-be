package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetItemRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.service.impl.AssignmentSheetServiceImpl;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentSheetServiceImplTest {

    @Mock
    private AssignmentSheetRepository assignmentSheetRepository;

    @Mock
    private AssignmentSheetItemRepository assignmentSheetItemRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private AssignmentSheetServiceImpl assignmentSheetService;

    private User teacher;
    private User student;
    private Classroom classroom;
    private AssignmentSheet masterSheet;
    private Assignment assignment;

    private final long teacherId = 1L;
    private final long studentId = 2L;
    private final long sheetId = 10L;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(teacherId);
        teacher.setEmail("teacher@codegym.com");
        teacher.setRole(Role.TEACHER);

        student = new User();
        student.setId(studentId);
        student.setEmail("student@codegym.com");
        student.setRole(Role.STUDENT);

        classroom = new Classroom();
        classroom.setId(100L);
        classroom.setClassCode("MATH2024");
        classroom.setTeacher(teacher);

        assignment = new Assignment();
        assignment.setId(50L);
        assignment.setTitle("Câu 1");
        assignment.setStatus(AssignmentStatus.DRAFT);
        assignment.setTeacher(teacher);

        masterSheet = new AssignmentSheet();
        masterSheet.setId(sheetId);
        masterSheet.setTitle("Đề thi giữa kỳ");
        masterSheet.setDescription("Mô tả đề thi");
        masterSheet.setTeacher(teacher);
        masterSheet.setClassroom(null);
        masterSheet.setItems(new ArrayList<>());
    }

    @Nested
    @DisplayName("publishAssignmentSheet Tests")
    class PublishAssignmentSheetTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when teacher does not exist")
        void publishAssignmentSheet_TeacherNotFound_ThrowsException() {
            PublishAssignmentSheetRequest request = new PublishAssignmentSheetRequest();
            request.setAssignmentIds(List.of(50L));

            when(userRepository.findById(teacherId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentSheetService.publishAssignmentSheet(request, teacherId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy giáo viên");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when no assignments provided or found")
        void publishAssignmentSheet_NoAssignments_ThrowsException() {
            PublishAssignmentSheetRequest request = new PublishAssignmentSheetRequest();
            request.setTitle("Đề thi rỗng");
            request.setAssignmentIds(List.of());

            when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
            when(assignmentSheetRepository.findByTeacherIdAndTitle(teacherId, "Đề thi rỗng")).thenReturn(List.of());

            assertThatThrownBy(() -> assignmentSheetService.publishAssignmentSheet(request, teacherId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Không tìm thấy bài tập nào để giao");
        }
    }

    @Nested
    @DisplayName("deleteAssignmentSheet Tests")
    class DeleteAssignmentSheetTests {

        @Test
        @DisplayName("Should delete master sheet successfully")
        void deleteAssignmentSheet_Success() {
            when(assignmentSheetRepository.findById(sheetId)).thenReturn(Optional.of(masterSheet));

            assignmentSheetService.deleteAssignmentSheet(sheetId, teacherId);

            verify(assignmentSheetRepository, times(1)).delete(masterSheet);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-owner teacher tries to delete sheet")
        void deleteAssignmentSheet_NotOwner_ThrowsAccessDeniedException() {
            long otherTeacherId = 999L;
            when(assignmentSheetRepository.findById(sheetId)).thenReturn(Optional.of(masterSheet));

            assertThatThrownBy(() -> assignmentSheetService.deleteAssignmentSheet(sheetId, otherTeacherId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền xóa phiếu bài tập này");
        }
    }

    @Nested
    @DisplayName("updateAssignmentSheet Tests")
    class UpdateAssignmentSheetTests {

        @Test
        @DisplayName("Should update sheet title and description successfully")
        void updateAssignmentSheet_Success() {
            UpdateAssignmentSheetRequest request = new UpdateAssignmentSheetRequest();
            request.setTitle("Mới");
            request.setDescription("Mô tả mới");

            when(assignmentSheetRepository.findById(sheetId)).thenReturn(Optional.of(masterSheet));
            when(assignmentSheetRepository.save(masterSheet)).thenReturn(masterSheet);

            AssignmentSheetResponse response = assignmentSheetService.updateAssignmentSheet(sheetId, request, teacherId);

            assertThat(response).isNotNull();
            assertThat(masterSheet.getTitle()).isEqualTo("Mới");
            assertThat(masterSheet.getDescription()).isEqualTo("Mô tả mới");
            verify(assignmentSheetRepository, times(1)).save(masterSheet);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when updating another teacher's sheet")
        void updateAssignmentSheet_NotOwner_ThrowsAccessDeniedException() {
            long nonOwnerId = 888L;
            UpdateAssignmentSheetRequest request = new UpdateAssignmentSheetRequest();

            when(assignmentSheetRepository.findById(sheetId)).thenReturn(Optional.of(masterSheet));

            assertThatThrownBy(() -> assignmentSheetService.updateAssignmentSheet(sheetId, request, nonOwnerId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền sửa phiếu bài tập này");
        }
    }
}
