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

    @Async
    public void sendBugReportStatusEmail(String toEmail, String fullName, String errorTypeLabel, String statusTitle, String statusMessage) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("[MathClass] Thông báo tiến độ xử lý báo cáo sự cố");

            String displayName = fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : toEmail;

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #2563eb;">Cập nhật tiến độ Báo cáo Sự cố</h2>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Hệ thống <strong>MathClass</strong> vừa cập nhật trạng thái cho báo cáo sự cố của bạn (Loại lỗi: <strong>%s</strong>).</p>
                    <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 12px; margin: 16px 0;">
                        <p style="margin: 0; font-weight: bold; color: #1d4ed8;">%s</p>
                        <p style="margin: 4px 0 0 0; color: #1e40af;">%s</p>
                    </div>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="font-size: 13px; color: #475569;">Cảm ơn bạn đã đóng góp phản hồi giúp nâng cao chất lượng dịch vụ của MathClass.</p>
                    <p style="font-size: 12px; color: #94a3b8; margin-top: 20px;">Trân trọng,<br/>Đội ngũ Kỹ thuật & Quản trị MathClass</p>
                </div>
                """.formatted(
                    HtmlUtils.htmlEscape(displayName),
                    HtmlUtils.htmlEscape(errorTypeLabel),
                    HtmlUtils.htmlEscape(statusTitle),
                    HtmlUtils.htmlEscape(statusMessage)
                );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("Bug report status email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send bug report status email to {}", toEmail, e);
        }
    }

    @Async
    public void sendBugReportOtpEmail(String toEmail, String otpCode) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("[MathClass] Mã xác thực OTP gửi báo cáo sự cố");

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #ea580c; margin-top: 0;">Mã OTP Xác thực Báo cáo Sự cố</h2>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Bạn (hoặc ai đó) đang yêu cầu gửi Báo cáo sự cố hệ thống tại <strong>MathClass</strong>. Mã OTP xác thực của bạn là:</p>
                    <div style="background-color: #fff7ed; border: 1px dashed #f97316; text-align: center; padding: 16px; margin: 20px 0; border-radius: 8px;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #c2410c;">%s</span>
                    </div>
                    <p style="font-size: 13px; color: #64748b;">⚠️ Mã OTP này có hiệu lực trong <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="font-size: 12px; color: #94a3b8;">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.<br/>Trân trọng,<br/>Đội ngũ Kỹ thuật & Quản trị MathClass</p>
                </div>
                """.formatted(
                    HtmlUtils.htmlEscape(toEmail),
                    HtmlUtils.htmlEscape(otpCode)
                );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("Bug report OTP email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send bug report OTP email to {}", toEmail, e);
        }
    }

    @Async
    public void sendSecurityAlertEmail(String toEmail, String fullName, LocalDateTime changeTime) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("[MathClass] Cảnh báo bảo mật: Mật khẩu đã được thay đổi");

            String formattedTime = changeTime != null ? changeTime.format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy")) : "";
            String displayName = fullName != null && !fullName.isBlank() ? fullName.trim() : toEmail;

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #4f46e5; margin-top: 0;">Thông báo thay đổi mật khẩu</h2>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Mật khẩu tài khoản của bạn tại <strong>MathClass</strong> vừa được thay đổi thành công vào lúc <strong>%s</strong>.</p>
                    <div style="background-color: #f0fdf4; border-left: 4px solid #16a34a; padding: 12px; margin: 16px 0;">
                        <p style="margin: 0; color: #15803d; font-size: 14px;">Trạng thái: <strong>Thay đổi mật khẩu thành công</strong></p>
                    </div>
                    <p style="font-size: 13px; color: #dc2626;">⚠️ Nếu bạn KHÔNG thực hiện thao tác này, tài khoản của bạn có thể đang gặp nguy hiểm. Vui lòng liên hệ ngay với Quản trị viên qua email support@mathclass.edu.vn.</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="font-size: 12px; color: #94a3b8;">Trân trọng,<br/>Đội ngũ Bảo mật MathClass</p>
                </div>
                """.formatted(
                    HtmlUtils.htmlEscape(displayName),
                    HtmlUtils.htmlEscape(formattedTime)
                );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("Security alert email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send security alert email to {}", toEmail, e);
        }
    }

    @Async
    public void sendSetPasswordOtpEmail(String toEmail, String fullName, String otpCode) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("[MathClass] Mã OTP xác thực thiết lập mật khẩu lần đầu");

            String displayName = fullName != null && !fullName.isBlank() ? fullName.trim() : toEmail;

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #4f46e5; margin-top: 0;">Mã OTP Thiết Lập Mật Khẩu</h2>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Bạn đang thực hiện yêu cầu thiết lập mật khẩu đăng nhập tại <strong>MathClass</strong>. Mã OTP xác thực của bạn là:</p>
                    <div style="background-color: #eef2ff; border: 1px dashed #6366f1; text-align: center; padding: 16px; margin: 20px 0; border-radius: 8px;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #4338ca;">%s</span>
                    </div>
                    <p style="font-size: 13px; color: #64748b;">⚠️ Mã OTP này có hiệu lực trong <strong>5 phút</strong>. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="font-size: 12px; color: #94a3b8;">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.<br/>Trân trọng,<br/>Đội ngũ Bảo mật MathClass</p>
                </div>
                """.formatted(
                    HtmlUtils.htmlEscape(displayName),
                    HtmlUtils.htmlEscape(otpCode)
                );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("Set password OTP email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send set password OTP email to {}", toEmail, e);
        }
    }
}


