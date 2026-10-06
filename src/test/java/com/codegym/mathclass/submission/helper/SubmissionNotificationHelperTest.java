package com.codegym.mathclass.submission.helper;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionNotificationHelperTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private SubmissionNotificationHelper helper;

    private User teacher;
    private User student;
    private Assignment assignment;
    private Submission submission;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(helper, "frontendUrl", "http://localhost:3000");

        teacher = new User();
        teacher.setId(1L);
        teacher.setFullName("Nguyen Van Teacher");
        teacher.setEmail("teacher@codegym.com");

        student = new User();
        student.setId(2L);
        student.setFullName("Tran Thi Student");
        student.setEmail("student@codegym.com");

        Classroom classroom = new Classroom();
        classroom.setClassCode("CLASS101");

        assignment = new Assignment();
        assignment.setId(10L);
        assignment.setTitle("Bài tập Hàm số");
        assignment.setTeacher(teacher);
        assignment.setClassroom(classroom);

        submission = new Submission();
        submission.setId(100L);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.SUBMITTED);
    }

    @Test
    @DisplayName("sendSubmissionNotification: Single assignment version 1 sends email and in-app to teacher")
    void sendSubmissionNotification_SingleAssignment_Version1() {
        helper.sendSubmissionNotification(submission, assignment, 1);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("teacher@codegym.com"),
                contains("đã nộp bài tập"),
                eq("submission-submitted"),
                any()
        );
        verify(notificationService, times(1)).saveAndSendNotification(
                eq(1L),
                contains("đã nộp bài tập"),
                eq("/assignments/10/submissions/100")
        );
    }

    @Test
    @DisplayName("sendSubmissionNotification: Single assignment version > 1 sends re-submit notification")
    void sendSubmissionNotification_SingleAssignment_Version2() {
        helper.sendSubmissionNotification(submission, assignment, 2);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("teacher@codegym.com"),
                contains("Lần 2"),
                eq("submission-submitted"),
                any()
        );
        verify(notificationService, times(1)).saveAndSendNotification(
                eq(1L),
                contains("Lần 2"),
                eq("/assignments/10/submissions/100")
        );
    }

    @Test
    @DisplayName("sendGradingNotification: Single assignment sends email and in-app to student")
    void sendGradingNotification_SingleAssignment_Success() {
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setScore(9.5);

        helper.sendGradingNotification(submission, assignment);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("student@codegym.com"),
                contains("Giáo viên đã chấm điểm bài tập"),
                eq("submission-graded"),
                any()
        );
        verify(notificationService, times(1)).saveAndSendNotification(
                eq(2L),
                contains("Giáo viên đã chấm điểm bài tập"),
                contains("/assignments/10?classCode=CLASS101")
        );
    }

    @Test
    @DisplayName("checkAndProcessSheetNotification: All sheet assignments completed triggers teacher notification")
    void checkAndProcessSheetNotification_AllCompleted_NotifiesTeacher() {
        AssignmentSheet sheet = new AssignmentSheet();
        sheet.setId(55L);
        sheet.setTitle("Phiếu ôn tập chương 1");
        assignment.setAssignmentSheet(sheet);

        Assignment assignment2 = new Assignment();
        assignment2.setId(11L);
        assignment2.setTitle("Bài tập Hình học");
        assignment2.setAssignmentSheet(sheet);
        assignment2.setTeacher(teacher);

        Submission sub2 = new Submission();
        sub2.setId(101L);
        sub2.setAssignment(assignment2);
        sub2.setStudent(student);
        sub2.setStatus(SubmissionStatus.SUBMITTED);

        when(assignmentRepository.findByAssignmentSheetId(55L)).thenReturn(List.of(assignment, assignment2));
        when(submissionRepository.findAllByAssignmentIdInAndStudentId(List.of(10L, 11L), 2L))
                .thenReturn(List.of(submission, sub2));

        helper.checkAndProcessSheetNotification(assignment, submission, false);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("teacher@codegym.com"),
                contains("đã hoàn thành phiếu bài tập: Phiếu ôn tập chương 1"),
                eq("submission-submitted"),
                any()
        );
        verify(notificationService, times(1)).saveAndSendNotification(
                eq(1L),
                contains("đã hoàn thành phiếu bài tập: Phiếu ôn tập chương 1"),
                anyString()
        );
    }

    @Test
    @DisplayName("checkAndProcessSheetNotification: Incomplete sheet does not trigger notification")
    void checkAndProcessSheetNotification_Incomplete_DoesNotNotify() {
        AssignmentSheet sheet = new AssignmentSheet();
        sheet.setId(55L);
        assignment.setAssignmentSheet(sheet);

        Assignment assignment2 = new Assignment();
        assignment2.setId(11L);

        when(assignmentRepository.findByAssignmentSheetId(55L)).thenReturn(List.of(assignment, assignment2));
        when(submissionRepository.findAllByAssignmentIdInAndStudentId(List.of(10L, 11L), 2L))
                .thenReturn(List.of(submission)); // only 1 of 2 submitted

        helper.checkAndProcessSheetNotification(assignment, submission, false);

        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
        verify(notificationService, never()).saveAndSendNotification(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("checkAndProcessSheetNotification: Null sheet does nothing")
    void checkAndProcessSheetNotification_NullSheet_DoesNothing() {
        assignment.setAssignmentSheet(null);
        helper.checkAndProcessSheetNotification(assignment, submission, false);

        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationService);
    }
}
