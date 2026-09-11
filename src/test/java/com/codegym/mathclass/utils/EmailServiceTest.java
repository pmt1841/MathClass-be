package com.codegym.mathclass.utils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "senderEmail", "noreply@mathclass.edu.vn");
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("sendHtmlMailAsync gửi email với nội dung template Thymeleaf thành công")
    void testSendHtmlMailAsync_Success() {
        Context context = new Context();
        context.setVariable("name", "Nguyễn Văn A");

        when(templateEngine.process(eq("welcome-email"), any(Context.class)))
                .thenReturn("<html><body>Xin chào Nguyễn Văn A</body></html>");

        emailService.sendHtmlMailAsync("user@mathclass.edu.vn", "Chào mừng", "welcome-email", context);

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendAccountLockedEmail gửi email thông báo tạm khóa tài khoản")
    void testSendAccountLockedEmail_Success() {
        assertDoesNotThrow(() ->
                emailService.sendAccountLockedEmail(
                        "user@mathclass.edu.vn", "Nguyễn Văn A",
                        "Vi phạm điều khoản dịch vụ", LocalDateTime.now()
                )
        );

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendAccountUnlockedEmail gửi email thông báo mở khóa tài khoản")
    void testSendAccountUnlockedEmail_Success() {
        assertDoesNotThrow(() ->
                emailService.sendAccountUnlockedEmail(
                        "user@mathclass.edu.vn", "Nguyễn Văn A",
                        "Đã xác minh thông tin", LocalDateTime.now()
                )
        );

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendBugReportStatusEmail gửi email cập nhật sự cố")
    void testSendBugReportStatusEmail_Success() {
        assertDoesNotThrow(() ->
                emailService.sendBugReportStatusEmail(
                        "reporter@mathclass.edu.vn", "Người báo lỗi",
                        "Giao diện", "Đã tiếp nhận", "Chúng tôi đang xử lý sự cố."
                )
        );

        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendBugReportOtpEmail gửi mã OTP xác thực báo cáo sự cố")
    void testSendBugReportOtpEmail_Success() {
        assertDoesNotThrow(() ->
                emailService.sendBugReportOtpEmail("reporter@mathclass.edu.vn", "123456")
        );

        verify(javaMailSender).send(mimeMessage);
    }
}
