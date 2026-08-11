package com.codegym.mathclass.user.event;

import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccountEventListener {

    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Async
    @EventListener
    public void handleUserAccountLocked(UserAccountLockedEvent event) {
        try {
            emailService.sendAccountLockedEmail(
                    event.userEmail(),
                    event.fullName(),
                    event.reason(),
                    event.lockedAt()
            );
            log.info("Gửi email thông báo khóa tài khoản thành công cho: {}", event.userEmail());
        } catch (Exception e) {
            log.error("Lỗi gửi email thông báo khóa tài khoản cho {}: {}", event.userEmail(), e.getMessage(), e);
            systemLogService.logWarning(
                    event.lockedByAdmin(),
                    "Gửi email thông báo khóa tài khoản thất bại cho " + event.userEmail() + ": " + e.getMessage(),
                    event.userId()
            );
        }
    }

    @Async
    @EventListener
    public void handleUserAccountUnlocked(UserAccountUnlockedEvent event) {
        try {
            emailService.sendAccountUnlockedEmail(
                    event.userEmail(),
                    event.fullName(),
                    event.unlockReason(),
                    event.unlockedAt()
            );
            log.info("Gửi email thông báo mở khóa tài khoản thành công cho: {}", event.userEmail());
        } catch (Exception e) {
            log.error("Lỗi gửi email thông báo mở khóa tài khoản cho {}: {}", event.userEmail(), e.getMessage(), e);
            systemLogService.logWarning(
                    event.performedBy(),
                    "Gửi email thông báo mở khóa tài khoản thất bại cho " + event.userEmail() + ": " + e.getMessage(),
                    event.userId()
            );
        }
    }
}

