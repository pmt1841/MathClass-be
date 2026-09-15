package com.codegym.mathclass.security.jwt;

public interface TokenBlacklistService {

    /**
     * Đưa token JWT vào danh sách đen (Blacklist) trên Redis với TTL bằng thời gian sống còn lại.
     *
     * @param token Chuỗi token JWT thô cần vô hiệu hóa
     */
    void blacklistToken(String token);

    /**
     * Kiểm tra xem token JWT đã bị đưa vào danh sách đen hay chưa.
     *
     * @param token Chuỗi token JWT thô
     * @return true nếu token nằm trong blacklist, ngược lại false
     */
    boolean isBlacklisted(String token);
}
