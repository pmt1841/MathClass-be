package com.codegym.mathclass.notification.strategy;

import com.codegym.mathclass.notification.dto.NotificationChannel;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationStrategyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private EmailNotificationStrategy emailStrategy;

    @BeforeEach
    void setUp() {
        emailStrategy = new EmailNotificationStrategy(userRepository, emailService);
    }

    @Test
    @DisplayName("Kiểm tra supports chỉ trả về true với channel EMAIL")
    void supports_ChannelCheck() {
        assertTrue(emailStrategy.supports(NotificationChannel.EMAIL));
        assertFalse(emailStrategy.supports(NotificationChannel.SSE));
    }

    @Test
    @DisplayName("Gửi email thành công khi tìm thấy user có địa chỉ email hợp lệ")
    void send_ValidUserWithEmail_CallsEmailService() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@gmail.com");
        user.setFullName("Nguyen Van A");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        NotificationPayload payload = new NotificationPayload(
                1L, "Bài tập mới", "Bạn có bài tập toán", "/assignments/1", "NOTIFICATION", null
        );

        emailStrategy.send(payload);

        verify(emailService, times(1)).sendHtmlMailAsync(
                eq("student@gmail.com"),
                contains("Bài tập mới"),
                eq("email-notification-template"),
                any(Context.class)
        );
    }

    @Test
    @DisplayName("Không gửi email khi user không tồn tại hoặc không có địa chỉ email")
    void send_UserNotFoundOrEmailBlank_DoesNotCallEmailService() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        NotificationPayload payload = new NotificationPayload(
                2L, "Title", "Msg", "/link", "EVENT", null
        );

        emailStrategy.send(payload);

        verifyNoInteractions(emailService);
    }
}
