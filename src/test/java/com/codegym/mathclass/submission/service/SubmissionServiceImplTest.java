package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.service.SubmissionServiceImpl;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(1L);

        student = new User();
        student.setId(2L);
        student.setFullName("John Doe");
        student.setEmail("john@gmail.com");

        assignment = new Assignment();
        assignment.setId(10L);
        assignment.setTeacher(teacher);
        assignment.setTitle("Math 101");
        assignment.setDeadline(LocalDateTime.now().plusDays(1));

        submission = new Submission();
        submission.setId(100L);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());
    }

    // ==========================================
    // Tests for createSubmission
    // ==========================================

    @Test
    @DisplayName("Should create submission successfully")
    void createSubmission_ValidData_ReturnsSubmissionResponse() {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(10L);
        request.setStatus(SubmissionStatus.SUBMITTED);
        request.setContent("My answers");

        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(submissionRepository.findFirstByAssignmentIdAndStudentId(10L, 2L)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission s = invocation.getArgument(0);
            s.setId(100L);
            return s;
        });

        // When
        SubmissionResponse response = submissionService.createSubmission(2L, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        verify(submissionRepository, times(1)).save(any(Submission.class));
        verify(notificationService, times(1)).saveAndSendNotification(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw Exception when create submission with no assignmentId")
    void createSubmission_NullAssignmentId_ThrowsException() {
        // Given
        SubmissionRequest request = new SubmissionRequest();

        // When & Then
        assertThatThrownBy(() -> submissionService.createSubmission(2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Thiếu assignmentId");
    }

    @Test
    @DisplayName("Should throw Exception when create submission after deadline")
    void createSubmission_AfterDeadline_ThrowsException() {
        // Given
        assignment.setDeadline(LocalDateTime.now().minusDays(1));
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(10L);

        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));

        // When & Then
        assertThatThrownBy(() -> submissionService.createSubmission(2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Đã hết hạn nộp bài tập");
    }

    @Test
    @DisplayName("Should throw Exception when create submission with empty content")
    void createSubmission_EmptyContent_ThrowsException() {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(10L);
        request.setStatus(SubmissionStatus.SUBMITTED);
        request.setContent("");

        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(submissionRepository.findFirstByAssignmentIdAndStudentId(10L, 2L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> submissionService.createSubmission(2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nội dung bài làm không được để trống khi nộp bài");
    }

    // ==========================================
    // Tests for updateSubmission
    // ==========================================

    @Test
    @DisplayName("Should update submission successfully")
    void updateSubmission_ValidData_ReturnsSubmissionResponse() {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setStatus(SubmissionStatus.SUBMITTED);
        request.setContent("Updated answers");

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

        // When
        SubmissionResponse response = submissionService.updateSubmission(100L, 2L, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }

    @Test
    @DisplayName("Should throw Exception when update submission not authorized")
    void updateSubmission_NotAuthorized_ThrowsException() {
        // Given
        SubmissionRequest request = new SubmissionRequest();

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        // When & Then
        assertThatThrownBy(() -> submissionService.updateSubmission(100L, 99L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn không có quyền sửa bài nộp này");
    }

    @Test
    @DisplayName("Should throw Exception when update graded submission")
    void updateSubmission_Graded_ThrowsException() {
        // Given
        submission.setScore(9.5);
        SubmissionRequest request = new SubmissionRequest();

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        // When & Then
        assertThatThrownBy(() -> submissionService.updateSubmission(100L, 2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Giáo viên đã chấm điểm, không thể sửa bài");
    }

    // ==========================================
    // Tests for unsubmitSubmission
    // ==========================================

    @Test
    @DisplayName("Should unsubmit submission successfully")
    void unsubmitSubmission_ValidData_ReturnsSubmissionResponse() {
        // Given
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

        // When
        SubmissionResponse response = submissionService.unsubmitSubmission(100L, 2L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }

    @Test
    @DisplayName("Should throw Exception when unsubmit draft submission")
    void unsubmitSubmission_Draft_ThrowsException() {
        // Given
        submission.setStatus(SubmissionStatus.DRAFT);
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        // When & Then
        assertThatThrownBy(() -> submissionService.unsubmitSubmission(100L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bài làm chưa được nộp");
    }

    // ==========================================
    // Tests for gradeSubmission
    // ==========================================

    @Test
    @DisplayName("Should grade submission successfully")
    void gradeSubmission_ValidData_ReturnsSubmissionResponse() {
        // Given
        GradeRequest request = new GradeRequest(9.5, "Good");

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

        // When
        SubmissionResponse response = submissionService.gradeSubmission(100L, 1L, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getScore()).isEqualTo(9.5);
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        verify(emailService, times(1)).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
        verify(notificationService, times(1)).saveAndSendNotification(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw Exception when teacher not authorized to grade")
    void gradeSubmission_NotAuthorized_ThrowsException() {
        // Given
        GradeRequest request = new GradeRequest(9.5, "Good");

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        // When & Then
        assertThatThrownBy(() -> submissionService.gradeSubmission(100L, 99L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn không có quyền chấm bài nộp này");
    }

    // ==========================================
    // Tests for getSubmissionsByAssignment
    // ==========================================

    @Test
    @DisplayName("Should get submissions by assignment successfully")
    void getSubmissionsByAssignment_ValidData_ReturnsPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Submission> page = new PageImpl<>(Collections.singletonList(submission));

        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findSubmissionsByAssignment(10L, SubmissionStatus.SUBMITTED, "", pageable))
                .thenReturn(page);

        // When
        Page<SubmissionResponse> responsePage = submissionService.getSubmissionsByAssignment(10L, 1L, SubmissionStatus.SUBMITTED, null, pageable);

        // Then
        assertThat(responsePage).isNotNull();
        assertThat(responsePage.getTotalElements()).isEqualTo(1);
        assertThat(responsePage.getContent().get(0).getId()).isEqualTo(100L);
    }

    // ==========================================
    // Security Regression Tests: LaTeX Injection
    // ==========================================

    @Test
    @DisplayName("Should throw BadRequestException when create submission contains dangerous LaTeX")
    void createSubmission_DangerousLaTeX_ThrowsBadRequestException() {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(10L);
        request.setStatus(SubmissionStatus.SUBMITTED);
        request.setContent("This is dangerous: \\input{/etc/passwd}");

        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(submissionRepository.findFirstByAssignmentIdAndStudentId(10L, 2L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> submissionService.createSubmission(2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nội dung bài làm chứa lệnh LaTeX không hợp lệ");
    }

    @Test
    @DisplayName("Should throw BadRequestException when update submission contains dangerous LaTeX")
    void updateSubmission_DangerousLaTeX_ThrowsBadRequestException() {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setStatus(SubmissionStatus.SUBMITTED);
        request.setContent("This is dangerous: \\write18{rm -rf /}");

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        // When & Then
        assertThatThrownBy(() -> submissionService.updateSubmission(100L, 2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nội dung bài làm chứa lệnh LaTeX không hợp lệ");
    }

    @Test
    @DisplayName("Should throw BadRequestException when grading with dangerous LaTeX in teacher feedback")
    void gradeSubmission_DangerousLaTeX_ThrowsBadRequestException() {
        // Given
        GradeRequest request = new GradeRequest(8.0, "Nice try, but: \\include{sensitive}");

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        // When & Then
        assertThatThrownBy(() -> submissionService.gradeSubmission(100L, 1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nội dung phản hồi chứa lệnh LaTeX không hợp lệ");
    }
}
