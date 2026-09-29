package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.service.PasswordRecoveryService;
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    public static final String PASSWORD_RESET_KEY_PREFIX = "auth:reset:";

    private final UserRepository userRepository;
    private final RateLimiterService rateLimiterService;
    private final RedissonClient redissonClient;
    private final EmailService emailService;
    private final PasswordEncoder encoder;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String rateLimitKey = "auth:forgot:" + email;
        if (!rateLimiterService.tryAcquire(rateLimitKey, Duration.ofSeconds(60))) {
            throw new TooManyRequestsException("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút.");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            String tokenHash = hashToken(rawToken);

            RBucket<String> resetBucket = redissonClient.getBucket(PASSWORD_RESET_KEY_PREFIX + tokenHash, StringCodec.INSTANCE);
            resetBucket.set(String.valueOf(user.getId()), Duration.ofMinutes(15));

            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

            sendEmailToUser(user, resetLink);
        }

        return new MessageResponse("Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư.");
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String rawToken = request.getToken();
        String tokenHash = hashToken(rawToken);

        RBucket<String> resetBucket = redissonClient.getBucket(PASSWORD_RESET_KEY_PREFIX + tokenHash, StringCodec.INSTANCE);
        String userIdStr = resetBucket.get();

        if (userIdStr == null) {
            throw new BadRequestException("Đường dẫn đặt lại mật khẩu không hợp lệ hoặc đã hết hạn (hiệu lực 15 phút).");
        }

        Long userId = Long.valueOf(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản người dùng."));

        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetBucket.delete();

        return new MessageResponse(
                "Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới.",
                user.getRole().name());
    }

    private void sendEmailToUser(User user, String resetLink) {
        Context context = new Context();
        context.setVariable("fullName", user.getFullName());
        context.setVariable("resetLink", resetLink);
        emailService.sendHtmlMailAsync(user.getEmail(), "Yêu cầu khôi phục mật khẩu MathClass", "forgot-password", context);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi thuật toán mã hóa SHA-256", e);
        }
    }
}
