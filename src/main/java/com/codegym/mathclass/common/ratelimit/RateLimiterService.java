package com.codegym.mathclass.common.ratelimit;

import java.time.Duration;

/**
 * Service quản lý giới hạn tần suất gọi (Rate Limiting) phân tán qua Redis.
 */
public interface RateLimiterService {

    /**
     * Thử lấy quyền thực thi (nguyên tử). Nếu key chưa tồn tại, ghi nhận và thiết lập thời gian làm mát (cooldown).
     * Phù hợp cho hành vi 1 lần / khoảng thời gian (như gửi OTP, yêu cầu quên mật khẩu).
     *
     * @param key      Định danh duy nhất (VD: email, user ID, IP)
     * @param cooldown Thời gian làm mát
     * @return true nếu lấy quyền thành công, false nếu đang bị giới hạn
     */
    boolean tryAcquire(String key, Duration cooldown);

    /**
     * Lấy số giây còn lại của thời gian làm mát.
     *
     * @param key Định danh duy nhất
     * @return số giây còn lại (0 nếu không bị giới hạn)
     */
    long getRemainingCooldownSeconds(String key);

    /**
     * Đặt thời gian làm mát cho key cụ thể.
     *
     * @param key      Định danh duy nhất
     * @param cooldown Thời gian làm mát
     */
    void setCooldown(String key, Duration cooldown);

    /**
     * Kiểm tra xem số lượng request đã chạm trần trong cửa sổ thời gian hay chưa.
     *
     * @param key         Định danh duy nhất
     * @param maxRequests Số lượt tối đa cho phép
     * @return true nếu đã vượt quá giới hạn, false nếu vẫn trong ngưỡng
     */
    boolean isWindowLimitExceeded(String key, long maxRequests);

    /**
     * Ghi nhận một lần gọi (hit) vào cửa sổ thời gian có thời hạn sống (TTL).
     *
     * @param key    Định danh duy nhất
     * @param window Cửa sổ thời gian
     */
    void recordHit(String key, Duration window);

    /**
     * Lấy số lượt đã ghi nhận trong cửa sổ thời gian.
     *
     * @param key Định danh duy nhất
     * @return số lượt đã gọi
     */
    long getHitCount(String key);
}
