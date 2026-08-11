package com.codegym.mathclass.user.event;

import java.time.LocalDateTime;

/**
 * Event đại diện cho hành động Mở khóa tài khoản thành công để xử lý bất đồng bộ (gửi email, audit log).
 */
public record UserAccountUnlockedEvent(
        Long userId,
        String userEmail,
        String fullName,
        String unlockReason,
        LocalDateTime unlockedAt,
        String performedBy
) {}
