package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubmissionServiceImplTest {

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

        assignment = new Assignment();
        assignment.setId(10L);
        assignment.setTeacher(teacher);

        submission = new Submission();
        submission.setId(100L);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());
    }

    @Test
    void testGetSubmissionsByAssignment_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Submission> page = new PageImpl<>(Collections.singletonList(submission));

        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findSubmissionsByAssignment(10L, SubmissionStatus.SUBMITTED, "John", pageable))
                .thenReturn(page);

        Page<SubmissionResponse> result = submissionService.getSubmissionsByAssignment(
                10L, 1L, SubmissionStatus.SUBMITTED, "John", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(100L, result.getContent().get(0).getId());
        assertEquals("John Doe", result.getContent().get(0).getStudentName());
    }

    @Test
    void testGetSubmissionsByAssignment_NotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            submissionService.getSubmissionsByAssignment(10L, 1L, null, null, pageable));
    }

    @Test
    void testGetSubmissionsByAssignment_AccessDenied() {
        Pageable pageable = PageRequest.of(0, 10);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));

        assertThrows(AccessDeniedException.class, () -> 
            submissionService.getSubmissionsByAssignment(10L, 99L, null, null, pageable));
    }

    @Test
    void testGetSubmissionDetail_Success() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        SubmissionResponse result = submissionService.getSubmissionDetail(100L, 1L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(10L, result.getAssignmentId());
    }

    @Test
    void testGetSubmissionDetail_NotFound() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            submissionService.getSubmissionDetail(100L, 1L));
    }

    @Test
    void testGetSubmissionDetail_AccessDenied() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        assertThrows(AccessDeniedException.class, () ->
            submissionService.getSubmissionDetail(100L, 99L));
    }

    @Test
    void testGradeSubmission_Success() {
        com.codegym.mathclass.submission.dto.GradeRequest gradeRequest = new com.codegym.mathclass.submission.dto.GradeRequest(9.5, "Good job!");
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(submission);

        SubmissionResponse result = submissionService.gradeSubmission(100L, 1L, gradeRequest);

        assertNotNull(result);
        assertEquals(SubmissionStatus.GRADED, submission.getStatus());
        assertEquals(9.5, submission.getScore());
        assertEquals("Good job!", submission.getTeacherFeedback());
        verify(notificationService, times(1)).saveAndSendNotification(eq(2L), anyString(), anyString());
    }

    @Test
    void testGradeSubmission_AccessDenied() {
        com.codegym.mathclass.submission.dto.GradeRequest gradeRequest = new com.codegym.mathclass.submission.dto.GradeRequest(9.5, "Good job!");
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        assertThrows(AccessDeniedException.class, () ->
            submissionService.gradeSubmission(100L, 99L, gradeRequest));
    }

    @Test
    void testGradeSubmission_DraftSubmission() {
        submission.setStatus(SubmissionStatus.DRAFT);
        com.codegym.mathclass.submission.dto.GradeRequest gradeRequest = new com.codegym.mathclass.submission.dto.GradeRequest(9.5, "Good job!");
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        assertThrows(com.codegym.mathclass.exception.BadRequestException.class, () ->
            submissionService.gradeSubmission(100L, 1L, gradeRequest));
    }
}
