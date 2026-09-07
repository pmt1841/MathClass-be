package com.codegym.mathclass.notification.strategy;

import com.codegym.mathclass.notification.dto.NotificationChannel;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import org.thymeleaf.context.Context;

@Component("emailNotificationStrategy")
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationStrategy implements NotificationStrategy {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationPayload payload) {
        if (payload == null || payload.recipientId() == null) {
            return;
        }

        User user = userRepository.findById(payload.recipientId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("[EmailStrategy] Không tìm thấy email cho user ID: {}", payload.recipientId());
            return;
        }

        String toEmail = user.getEmail();
        String fullName = user.getFullName() != null ? user.getFullName() : "Nguoidung";
        String title = payload.title() != null && !payload.title().isBlank()
                ? payload.title()
                : "Thông báo từ MathClass";
        String message = payload.message() != null ? payload.message() : "";
        String link = payload.link() != null ? payload.link() : "";

        log.info("[EmailStrategy] Đang gửi mail thông báo tới: {} ({})", toEmail, title);

        try {
            Context context = new Context();
            context.setVariable("fullName", HtmlUtils.htmlEscape(fullName));
            context.setVariable("title", HtmlUtils.htmlEscape(title));
            context.setVariable("message", HtmlUtils.htmlEscape(message));
            context.setVariable("link", link);

            emailService.sendHtmlMailAsync(toEmail, "[MathClass] " + title, "email-notification-template", context);
        } catch (Exception e) {
            log.error("[EmailStrategy] Lỗi khi gửi email thông báo tới {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
