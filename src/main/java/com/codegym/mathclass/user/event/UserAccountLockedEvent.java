package com.codegym.mathclass.user.event;

import java.time.LocalDateTime;

/**
 * Event đại diện cho hành động Khóa tài khoản thành công để xử lý bất đồng bộ (gửi email, audit log).
 */
public record UserAccountLockedEvent(
        Long userId,
        String userEmail,
        String fullName,
        String reason,
        LocalDateTime lockedAt,
        String lockedByAdmin
) {}
