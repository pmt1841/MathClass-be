package com.codegym.mathclass.assignment.listener;

import com.codegym.mathclass.assignment.event.AssignmentPublishedEvent;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentNotificationListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AssignmentNotificationListener listener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "frontendUrl", "http://localhost:3000");
    }

    @Test
    @DisplayName("Should send email to all valid student recipients")
    void handleAssignmentPublished_ValidEventWithRecipients_SendsEmails() {
        AssignmentPublishedEvent.StudentRecipient student1 =
                new AssignmentPublishedEvent.StudentRecipient(1L, "student1@gmail.com", "Nguyễn Văn A");
        AssignmentPublishedEvent.StudentRecipient student2 =
                new AssignmentPublishedEvent.StudentRecipient(2L, "student2@gmail.com", "Trần Thị B");

        AssignmentPublishedEvent.PublishedTarget target =
                new AssignmentPublishedEvent.PublishedTarget(100L, "Bài tập Hình học", "MATH101", "Lớp 10A1",
                        List.of(student1, student2));

        AssignmentPublishedEvent event = new AssignmentPublishedEvent(10L, 1L, List.of(target));

        listener.handleAssignmentPublished(event);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("student1@gmail.com"),
                eq("Bài tập mới: Bài tập Hình học"),
                eq("assignment-notification"),
                contextCaptor.capture());

        Context context1 = contextCaptor.getValue();
        assertThat(context1.getVariable("studentName")).isEqualTo("Nguyễn Văn A");
        assertThat(context1.getVariable("assignmentName")).isEqualTo("Bài tập Hình học");
        assertThat(context1.getVariable("link")).isEqualTo("http://localhost:3000/assignments/100");

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("student2@gmail.com"),
                eq("Bài tập mới: Bài tập Hình học"),
                eq("assignment-notification"),
                any(Context.class));
    }

    @Test
    @DisplayName("Should do nothing when event or targets are null or empty")
    void handleAssignmentPublished_NullOrEmptyEventOrTargets_DoesNothing() {
        listener.handleAssignmentPublished(null);
        listener.handleAssignmentPublished(new AssignmentPublishedEvent(10L, 1L, null));
        listener.handleAssignmentPublished(new AssignmentPublishedEvent(10L, 1L, List.of()));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Should do nothing when target recipients are null or empty")
    void handleAssignmentPublished_TargetWithNullOrEmptyRecipients_DoesNothing() {
        AssignmentPublishedEvent.PublishedTarget target1 =
                new AssignmentPublishedEvent.PublishedTarget(100L, "Bài tập 1", "MATH101", "Lớp 10A1", null);
        AssignmentPublishedEvent.PublishedTarget target2 =
                new AssignmentPublishedEvent.PublishedTarget(101L, "Bài tập 2", "MATH102", "Lớp 10A2", List.of());

        AssignmentPublishedEvent event = new AssignmentPublishedEvent(10L, 1L, List.of(target1, target2));

        listener.handleAssignmentPublished(event);

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Should skip student recipient with null or blank email")
    void handleAssignmentPublished_StudentWithNullOrBlankEmail_SkipsRecipient() {
        AssignmentPublishedEvent.StudentRecipient studentNullEmail =
                new AssignmentPublishedEvent.StudentRecipient(1L, null, "Không có email");
        AssignmentPublishedEvent.StudentRecipient studentBlankEmail =
                new AssignmentPublishedEvent.StudentRecipient(2L, "   ", "Email rỗng");
        AssignmentPublishedEvent.StudentRecipient studentValidEmail =
                new AssignmentPublishedEvent.StudentRecipient(3L, "valid@gmail.com", "Học sinh chuẩn");

        AssignmentPublishedEvent.PublishedTarget target =
                new AssignmentPublishedEvent.PublishedTarget(100L, "Bài tập Đại số", "MATH101", "Lớp 10A1",
                        List.of(studentNullEmail, studentBlankEmail, studentValidEmail));

        AssignmentPublishedEvent event = new AssignmentPublishedEvent(10L, 1L, List.of(target));

        listener.handleAssignmentPublished(event);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("valid@gmail.com"),
                eq("Bài tập mới: Bài tập Đại số"),
                eq("assignment-notification"),
                any(Context.class));
        verifyNoMoreInteractions(emailService);
    }

    @Test
    @DisplayName("Should catch exception and not crash when emailService throws error")
    void handleAssignmentPublished_EmailServiceThrowsException_DoesNotCrash() {
        AssignmentPublishedEvent.StudentRecipient student =
                new AssignmentPublishedEvent.StudentRecipient(1L, "student@gmail.com", "Nguyễn Văn A");
        AssignmentPublishedEvent.PublishedTarget target =
                new AssignmentPublishedEvent.PublishedTarget(100L, "Bài tập 1", "MATH101", "Lớp 10A1", List.of(student));
        AssignmentPublishedEvent event = new AssignmentPublishedEvent(10L, 1L, List.of(target));

        doThrow(new RuntimeException("Mail server is down"))
                .when(emailService)
                .sendHtmlMailAsync(anyString(), anyString(), anyString(), any(Context.class));

        assertThatCode(() -> listener.handleAssignmentPublished(event))
                .doesNotThrowAnyException();
    }
}
