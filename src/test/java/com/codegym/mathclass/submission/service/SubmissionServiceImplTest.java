package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.service.impl.SubmissionServiceImpl;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    private Assignment assignment;
    private User teacher;
    private User student;
    private Submission submission;

    private final long teacherId = 1L;
    private final long studentId = 2L;
    private final long assignmentId = 10L;
    private final long submissionId = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(submissionService, "frontendUrl", "http://localhost:3000");

        teacher = new User();
        teacher.setId(teacherId);
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setEmail("teacher@codegym.com");

        student = new User();
        student.setId(studentId);
        student.setFullName("Tran Thi Student");
        student.setEmail("student@codegym.com");

        assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTeacher(teacher);
        assignment.setTitle("Math 101 Assignment");
        assignment.setDeadline(LocalDateTime.now().plusDays(1));

        submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setContent("Sample submission content");
        submission.setSubmittedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("createSubmission Tests")
    class CreateSubmissionTests {

        @Test
        @DisplayName("Should create and save draft submission successfully")
        void createSubmission_SaveDraft_Success() {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(assignmentId);
            request.setStatus(SubmissionStatus.DRAFT);
            request.setContent("Draft content");

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId))
                    .thenReturn(Optional.empty());
            when(submissionRepository.save(any(Submission.class))).thenAnswer(i -> {
                Submission s = i.getArgument(0);
                s.setId(submissionId);
                return s;
            });

            SubmissionResponse response = submissionService.createSubmission(studentId, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
            verify(submissionRepository, times(1)).save(any(Submission.class));
            verify(notificationService, never()).saveAndSendNotification(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should create submitted submission successfully and send notification")
        void createSubmission_Submit_Success() {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(assignmentId);
            request.setStatus(SubmissionStatus.SUBMITTED);
            request.setContent("Final answer");

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId))
                    .thenReturn(Optional.empty());
            when(submissionRepository.save(any(Submission.class))).thenAnswer(i -> {
                Submission s = i.getArgument(0);
                s.setId(submissionId);
                return s;
            });

            SubmissionResponse response = submissionService.createSubmission(studentId, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
            verify(submissionRepository, times(1)).save(any(Submission.class));
            verify(emailService, times(1)).sendHtmlMailAsync(eq("teacher@codegym.com"), anyString(), eq("submission-submitted"), any());
            verify(notificationService, times(1)).saveAndSendNotification(eq(teacherId), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw BadRequestException when assignmentId is null")
        void createSubmission_NullAssignmentId_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();

            assertThatThrownBy(() -> submissionService.createSubmission(studentId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Thiếu assignmentId");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment not found")
        void createSubmission_AssignmentNotFound_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(999L);

            when(assignmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.createSubmission(studentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bài tập");
        }

        @Test
        @DisplayName("Should throw BadRequestException when submitting after deadline")
        void createSubmission_AfterDeadline_ThrowsException() {
            assignment.setDeadline(LocalDateTime.now().minusDays(1));
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(assignmentId);

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

            assertThatThrownBy(() -> submissionService.createSubmission(studentId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Đã hết hạn nộp bài tập");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when student not found")
        void createSubmission_StudentNotFound_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(assignmentId);

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(userRepository.findById(studentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.createSubmission(studentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy học sinh");
        }

        @Test
        @DisplayName("Should throw BadRequestException when submitting empty content")
        void createSubmission_EmptyContentOnSubmit_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(assignmentId);
            request.setStatus(SubmissionStatus.SUBMITTED);
            request.setContent("   ");

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.createSubmission(studentId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nội dung bài làm không được để trống khi nộp bài");
        }

        @Test
        @DisplayName("Should throw BadRequestException when content contains dangerous LaTeX")
        void createSubmission_DangerousLaTeX_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(assignmentId);
            request.setStatus(SubmissionStatus.SUBMITTED);
            request.setContent("\\input{/etc/passwd}");

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> submissionService.createSubmission(studentId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nội dung bài làm chứa lệnh LaTeX không hợp lệ");
        }
    }

    @Nested
    @DisplayName("updateSubmission Tests")
    class UpdateSubmissionTests {

        @Test
        @DisplayName("Should update submission successfully")
        void updateSubmission_ValidData_ReturnsSubmissionResponse() {
            SubmissionRequest request = new SubmissionRequest();
            request.setStatus(SubmissionStatus.SUBMITTED);
            request.setContent("Updated answers");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

            SubmissionResponse response = submissionService.updateSubmission(submissionId, studentId, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(submissionId);
            verify(submissionRepository, times(1)).save(any(Submission.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when submission not found")
        void updateSubmission_NotFound_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();

            when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.updateSubmission(999L, studentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bài nộp");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when student is not submission owner")
        void updateSubmission_NotOwner_ThrowsException() {
            SubmissionRequest request = new SubmissionRequest();

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.updateSubmission(submissionId, 999L, request))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền sửa bài nộp này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when updating after deadline")
        void updateSubmission_DeadlinePassed_ThrowsException() {
            assignment.setDeadline(LocalDateTime.now().minusDays(1));
            SubmissionRequest request = new SubmissionRequest();

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.updateSubmission(submissionId, studentId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Đã hết hạn nộp bài tập");
        }

        @Test
        @DisplayName("Should throw BadRequestException when submission is already graded")
        void updateSubmission_AlreadyGraded_ThrowsException() {
            submission.setScore(9.5);
            SubmissionRequest request = new SubmissionRequest();

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.updateSubmission(submissionId, studentId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Giáo viên đã chấm điểm, không thể sửa bài");
        }
    }

    @Nested
    @DisplayName("unsubmitSubmission Tests")
    class UnsubmitSubmissionTests {

        @Test
        @DisplayName("Should unsubmit submission successfully")
        void unsubmitSubmission_ValidData_ReturnsSubmissionResponse() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

            SubmissionResponse response = submissionService.unsubmitSubmission(submissionId, studentId);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
            verify(submissionRepository, times(1)).save(any(Submission.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-owner tries to unsubmit")
        void unsubmitSubmission_NotOwner_ThrowsException() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.unsubmitSubmission(submissionId, 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền hủy bài nộp này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when unsubmitting after deadline")
        void unsubmitSubmission_DeadlinePassed_ThrowsException() {
            assignment.setDeadline(LocalDateTime.now().minusDays(1));
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.unsubmitSubmission(submissionId, studentId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Đã hết hạn nộp bài tập, không thể hủy nộp");
        }

        @Test
        @DisplayName("Should throw BadRequestException when unsubmitting graded submission")
        void unsubmitSubmission_AlreadyGraded_ThrowsException() {
            submission.setScore(9.0);
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.unsubmitSubmission(submissionId, studentId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Giáo viên đã chấm điểm, không thể hủy nộp");
        }

        @Test
        @DisplayName("Should throw BadRequestException when unsubmitting draft submission")
        void unsubmitSubmission_DraftStatus_ThrowsException() {
            submission.setStatus(SubmissionStatus.DRAFT);
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.unsubmitSubmission(submissionId, studentId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bài làm chưa được nộp");
        }
    }

    @Nested
    @DisplayName("gradeSubmission Tests")
    class GradeSubmissionTests {

        @Test
        @DisplayName("Should grade submission successfully and send notifications")
        void gradeSubmission_ValidData_ReturnsSubmissionResponse() {
            GradeRequest request = new GradeRequest(9.5, "Good job!");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

            SubmissionResponse response = submissionService.gradeSubmission(submissionId, teacherId, request);

            assertThat(response).isNotNull();
            assertThat(response.getScore()).isEqualTo(9.5);
            assertThat(response.getStatus()).isEqualTo(SubmissionStatus.GRADED);
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@codegym.com"), anyString(), eq("submission-graded"), any());
            verify(notificationService, times(1)).saveAndSendNotification(eq(studentId), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when teacher is not assignment teacher")
        void gradeSubmission_NotAuthorized_ThrowsException() {
            GradeRequest request = new GradeRequest(9.5, "Good");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.gradeSubmission(submissionId, 999L, request))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền chấm bài nộp này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when grading draft submission")
        void gradeSubmission_DraftStatus_ThrowsException() {
            submission.setStatus(SubmissionStatus.DRAFT);
            GradeRequest request = new GradeRequest(9.5, "Good");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.gradeSubmission(submissionId, teacherId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Học sinh chưa nộp bài");
        }

        @Test
        @DisplayName("Should throw BadRequestException when teacher feedback contains dangerous LaTeX")
        void gradeSubmission_DangerousLaTeX_ThrowsException() {
            GradeRequest request = new GradeRequest(8.0, "Check: \\include{sensitive}");

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.gradeSubmission(submissionId, teacherId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nội dung phản hồi chứa lệnh LaTeX không hợp lệ");
        }
    }

    @Nested
    @DisplayName("getMySubmission Tests")
    class GetMySubmissionTests {

        @Test
        @DisplayName("Should return submission response when submission exists")
        void getMySubmission_Exists_ReturnsResponse() {
            when(submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));

            SubmissionResponse response = submissionService.getMySubmission(assignmentId, studentId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(submissionId);
        }

        @Test
        @DisplayName("Should return null when submission does not exist")
        void getMySubmission_NotExists_ReturnsNull() {
            when(submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId))
                    .thenReturn(Optional.empty());

            SubmissionResponse response = submissionService.getMySubmission(assignmentId, studentId);

            assertThat(response).isNull();
        }
    }

    @Nested
    @DisplayName("getSubmissionsByAssignment Tests")
    class GetSubmissionsByAssignmentTests {

        @Test
        @DisplayName("Should return page of submissions for assignment teacher")
        void getSubmissionsByAssignment_ValidTeacher_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Submission> page = new PageImpl<>(Collections.singletonList(submission));

            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findSubmissionsByAssignment(eq(assignmentId), eq(SubmissionStatus.SUBMITTED), any(), eq(pageable)))
                    .thenReturn(page);

            Page<SubmissionResponse> responsePage = submissionService.getSubmissionsByAssignment(
                    assignmentId, teacherId, SubmissionStatus.SUBMITTED, "john", pageable);

            assertThat(responsePage).isNotNull();
            assertThat(responsePage.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when assignment not found")
        void getSubmissionsByAssignment_AssignmentNotFound_ThrowsException() {
            Pageable pageable = PageRequest.of(0, 10);
            when(assignmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.getSubmissionsByAssignment(999L, teacherId, null, null, pageable))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bài tập");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-owner teacher requests submissions")
        void getSubmissionsByAssignment_NotOwnerTeacher_ThrowsException() {
            Pageable pageable = PageRequest.of(0, 10);
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

            assertThatThrownBy(() -> submissionService.getSubmissionsByAssignment(assignmentId, 999L, null, null, pageable))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền xem danh sách bài nộp này");
        }
    }

    @Nested
    @DisplayName("getSubmissionDetail Tests")
    class GetSubmissionDetailTests {

        @Test
        @DisplayName("Should return submission detail for owner teacher")
        void getSubmissionDetail_ValidTeacher_ReturnsResponse() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            SubmissionResponse response = submissionService.getSubmissionDetail(submissionId, teacherId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(submissionId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException for non-owner teacher")
        void getSubmissionDetail_NotOwnerTeacher_ThrowsException() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionService.getSubmissionDetail(submissionId, 999L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền xem bài nộp này");
        }
    }
}
