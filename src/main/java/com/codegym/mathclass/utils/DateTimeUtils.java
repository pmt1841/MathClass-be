package com.codegym.mathclass.utils;

import java.time.LocalDateTime;

/**
 * Tiện ích xử lý và chuẩn hóa thời gian giữa Client (Frontend UTC+7) và Database (UTC).
 */
public final class DateTimeUtils {

    private static final int VIETNAM_HOURS_OFFSET = 7;

    private DateTimeUtils() {
        // Utility class
    }

    /**
     * Chuyển đổi thời gian từ giờ địa phương Việt Nam (UTC+7) sang UTC tương ứng (trừ 7 giờ).
     * Frontend gửi ISO string giờ địa phương (ví dụ 23:59:59), lưu CSDL dưới dạng UTC để nhất quán.
     *
     * @param vietnamLocalTime thời gian theo giờ Việt Nam
     * @return thời gian đã quy đổi sang UTC, hoặc null nếu tham số truyền vào là null
     */
    public static LocalDateTime convertVietnamLocalToUtc(LocalDateTime vietnamLocalTime) {
        if (vietnamLocalTime == null) {
            return null;
        }
        return vietnamLocalTime.minusHours(VIETNAM_HOURS_OFFSET);
    }
}
