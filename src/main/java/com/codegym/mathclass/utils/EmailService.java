package com.codegym.mathclass.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Async
    public void sendHtmlMailAsync(String toEmail, String subject, String templateName, Context context) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);

            String htmlContent = templateEngine.process(templateName, context);
            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("Email sent successfully to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", toEmail, e);
        }
    }

    @Async
    public void sendAccountLockedEmail(String toEmail, String fullName, String reason, LocalDateTime lockedAt) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("[MathClass] Thông báo tạm khóa tài khoản người dùng");

            String formattedTime = lockedAt != null ? lockedAt.format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")) : "";
            String escapedReason = HtmlUtils.htmlEscape(reason != null && !reason.isBlank() ? reason.trim() : "Không có lý do chi tiết");



            String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; rounded-radius: 8px;">
                    <h2 style="color: #dc2626;">Thông báo tạm khóa tài khoản</h2>
                    <p>Xin chào <strong>%s</strong> (%s),</p>
                    <p>Tài khoản của bạn trên hệ thống <strong>MathClass</strong> đã bị tạm khóa bởi Quản trị viên.</p>
                    <div style="background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px; margin: 16px 0;">
                        <p style="margin: 0; font-weight: bold; color: #991b1b;">Lý do khóa:</p>
                        <p style="margin: 4px 0 0 0; color: #7f1d1d;">%s</p>
                    </div>
                    <p style="font-size: 13px; color: #64748b;">Thời điểm thực hiện: %s</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="font-size: 13px; color: #475569;">Nếu bạn tin rằng đây là sự nhầm lẫn hoặc cần giải trình thêm, vui lòng liên hệ Ban quản trị qua email support@mathclass.edu.vn.</p>
                    <p style="font-size: 12px; color: #94a3b8; margin-top: 20px;">Trân trọng,<br/>Đội ngũ Quản trị MathClass</p>
                </div>
                """.formatted(
                    HtmlUtils.htmlEscape(fullName != null ? fullName : "Nguoidung"),
                    HtmlUtils.htmlEscape(toEmail),
                    escapedReason,
                    formattedTime
                );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("Account locked email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account locked email to {}", toEmail, e);
            throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
        }
    }

    @Async
    public void sendAccountUnlockedEmail(String toEmail, String fullName, String unlockReason, LocalDateTime unlockedAt) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("[MathClass] Thông báo khôi phục / mở khóa tài khoản người dùng");

            String formattedTime = unlockedAt != null ? unlockedAt.format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")) : "";
            boolean hasReason = unlockReason != null && !unlockReason.trim().isEmpty();
            String escapedReason = hasReason ? HtmlUtils.htmlEscape(unlockReason.trim()) : "";




            String reasonBlock = hasReason ? """
                    <div style="background-color: #f0fdf4; border-left: 4px solid #22c55e; padding: 12px; margin: 16px 0;">
                        <p style="margin: 0; font-weight: bold; color: #15803d;">Ghi chú / Lý do mở khóa:</p>
                        <p style="margin: 4px 0 0 0; color: #166534;">%s</p>
                    </div>
                    """.formatted(escapedReason) : "";

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #16a34a;">Thông báo khôi phục tài khoản</h2>
                    <p>Xin chào <strong>%s</strong> (%s),</p>
                    <p>Tài khoản của bạn trên hệ thống <strong>MathClass</strong> đã được Quản trị viên khôi phục và mở khóa thành công. Giờ đây bạn đã có thể tiếp tục sử dụng tất cả các dịch vụ của hệ thống.</p>
                    %s
                    <p style="font-size: 13px; color: #64748b;">Thời điểm thực hiện: %s</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="font-size: 13px; color: #475569;">Vui lòng truy cập trang web MathClass để đăng nhập và tiếp tục công việc của bạn.</p>
                    <p style="font-size: 12px; color: #94a3b8; margin-top: 20px;">Trân trọng,<br/>Đội ngũ Quản trị MathClass</p>
                </div>
                """.formatted(
                    HtmlUtils.htmlEscape(fullName != null ? fullName : "Nguoidung"),
                    HtmlUtils.htmlEscape(toEmail),
                    reasonBlock,
                    formattedTime
                );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("Account unlocked email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account unlocked email to {}", toEmail, e);
            throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
        }
    }
}


