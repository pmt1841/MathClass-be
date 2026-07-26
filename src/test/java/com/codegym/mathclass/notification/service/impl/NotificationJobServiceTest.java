package com.codegym.mathclass.notification.service.impl;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationJobServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationJobService notificationJobService;

    private User student;
    private Classroom classroom;
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationJobService, "frontendUrl", "http://localhost:3000");

        student = new User();
        student.setId(1L);
        student.setEmail("student@test.com");
        student.setFullName("Student A");

        classroom = new Classroom();
        classroom.setId(10L);
        classroom.setClassName("Math 101");
        classroom.setStudents(Set.of(student));

        assignment = new Assignment();
        assignment.setId(100L);
        assignment.setTitle("Final Exam Practice");
        assignment.setDeadline(LocalDateTime.now().plusHours(23).plusMinutes(30));
        assignment.setClassroom(classroom);
        assignment.setReminderSent(false);
    }

    @Nested
    @DisplayName("sendDeadlineReminders Cronjob Tests")
    class SendDeadlineRemindersTests {

        @Test
        @DisplayName("Should send reminder email to unsubmitted students with enabled settings")
        void sendDeadlineReminders_EligibleStudent_SendsEmail() {
            when(assignmentRepository.findByDeadlineBetweenAndIsReminderSentFalseAndStatus(
                    any(LocalDateTime.class), any(LocalDateTime.class), eq(AssignmentStatus.PUBLISHED)))
                    .thenReturn(List.of(assignment));

            when(submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(100L, 1L, SubmissionStatus.DRAFT))
                    .thenReturn(false);

            NotificationSettings settings = NotificationSettings.builder()
                    .userId(1L)
                    .masterEmail(true)
                    .studentDeadlineReminder(true)
                    .build();

            when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

            notificationJobService.sendDeadlineReminders();

            verify(emailService, times(1)).sendHtmlMailAsync(
                    eq("student@test.com"),
                    contains("Final Exam Practice"),
                    eq("assignment-reminder"),
                    any()
            );
            verify(assignmentRepository, times(1)).save(assignment);
        }

        @Test
        @DisplayName("Should skip sending email when student has submitted assignment")
        void sendDeadlineReminders_AlreadySubmitted_SkipsEmail() {
            when(assignmentRepository.findByDeadlineBetweenAndIsReminderSentFalseAndStatus(
                    any(LocalDateTime.class), any(LocalDateTime.class), eq(AssignmentStatus.PUBLISHED)))
                    .thenReturn(List.of(assignment));

            when(submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(100L, 1L, SubmissionStatus.DRAFT))
                    .thenReturn(true);

            notificationJobService.sendDeadlineReminders();

            verify(emailService, never()).sendHtmlMailAsync(any(), any(), any(), any());
            verify(assignmentRepository, times(1)).save(assignment);
        }

        @Test
        @DisplayName("Should skip sending email when student disabled deadline reminder setting")
        void sendDeadlineReminders_DisabledSetting_SkipsEmail() {
            when(assignmentRepository.findByDeadlineBetweenAndIsReminderSentFalseAndStatus(
                    any(LocalDateTime.class), any(LocalDateTime.class), eq(AssignmentStatus.PUBLISHED)))
                    .thenReturn(List.of(assignment));

            when(submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(100L, 1L, SubmissionStatus.DRAFT))
                    .thenReturn(false);

            NotificationSettings disabledSettings = NotificationSettings.builder()
                    .userId(1L)
                    .masterEmail(true)
                    .studentDeadlineReminder(false)
                    .build();

            when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(disabledSettings));

            notificationJobService.sendDeadlineReminders();

            verify(emailService, never()).sendHtmlMailAsync(any(), any(), any(), any());
            verify(assignmentRepository, times(1)).save(assignment);
        }

        @Test
        @DisplayName("Should do nothing when no assignments are in deadline window")
        void sendDeadlineReminders_NoAssignmentsFound_DoesNothing() {
            when(assignmentRepository.findByDeadlineBetweenAndIsReminderSentFalseAndStatus(
                    any(LocalDateTime.class), any(LocalDateTime.class), eq(AssignmentStatus.PUBLISHED)))
                    .thenReturn(Collections.emptyList());

            notificationJobService.sendDeadlineReminders();

            verify(submissionRepository, never()).existsByAssignmentIdAndStudentIdAndStatusNot(anyLong(), anyLong(), any());
            verify(emailService, never()).sendHtmlMailAsync(any(), any(), any(), any());
        }
    }
}
