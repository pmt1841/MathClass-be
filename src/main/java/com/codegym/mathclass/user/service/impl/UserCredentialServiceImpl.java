package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.user.entity.PasswordHistory;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.PasswordHistoryRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.UserCredentialService;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserCredentialServiceImpl implements UserCredentialService {

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final RateLimiterService rateLimiterService;
    private final RedissonClient redissonClient;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String SET_PASSWORD_COOLDOWN_PREFIX = "auth:set-password:cooldown:";
    private static final String SET_PASSWORD_OTP_PREFIX = "auth:set-password:otp:";
    private static final String SET_PASSWORD_ATTEMPTS_PREFIX = "auth:set-password:attempts:";

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserById(userId);

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new BadRequestException("Tài khoản chưa có mật khẩu. Vui lòng sử dụng tính năng 'Thiết lập mật khẩu đăng nhập' qua OTP.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không trùng khớp với mật khẩu mới");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        applyNewPassword(user, request.getNewPassword());
    }

    @Override
    public void sendSetPasswordOtp(Long userId) {
        User user = findUserById(userId);

        if (!rateLimiterService.tryAcquire(SET_PASSWORD_COOLDOWN_PREFIX + userId, Duration.ofSeconds(60))) {
            long remaining = rateLimiterService.getRemainingCooldownSeconds(SET_PASSWORD_COOLDOWN_PREFIX + userId);
            throw new TooManyRequestsException("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau " + remaining + " giây.");
        }

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redissonClient.getBucket(SET_PASSWORD_OTP_PREFIX + userId, StringCodec.INSTANCE).set(otpCode, Duration.ofMinutes(5));
        redissonClient.getAtomicLong(SET_PASSWORD_ATTEMPTS_PREFIX + userId).delete();

        emailService.sendSetPasswordOtpEmail(user.getEmail(), user.getFullName(), otpCode);
    }

    @Override
    @Transactional
    public void setPassword(Long userId, SetPasswordRequest request) {
        User user = findUserById(userId);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không trùng khớp với mật khẩu mới");
        }

        validateOtp(userId, request.getOtpCode());

        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại");
            }
        }

        applyNewPassword(user, request.getNewPassword());
    }

    private void validateOtp(Long userId, String inputOtp) {
        RBucket<String> otpBucket = redissonClient.getBucket(SET_PASSWORD_OTP_PREFIX + userId, StringCodec.INSTANCE);
        String savedOtp = otpBucket.get();
        if (savedOtp == null || savedOtp.isBlank()) {
            throw new BadRequestException("Mã OTP chưa được gửi hoặc đã hết hạn (hiệu lực 5 phút). Vui lòng bấm 'Gửi mã xác thực' để nhận mã mới.");
        }

        RAtomicLong attempts = redissonClient.getAtomicLong(SET_PASSWORD_ATTEMPTS_PREFIX + userId);
        if (attempts.get() >= 5) {
            otpBucket.delete();
            attempts.delete();
            throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy, vui lòng yêu cầu mã mới.");
        }

        if (!savedOtp.equals(inputOtp.trim())) {
            long failedCount = attempts.incrementAndGet();
            attempts.expire(Duration.ofMinutes(5));
            if (failedCount >= 5) {
                otpBucket.delete();
                attempts.delete();
                throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy, vui lòng yêu cầu mã mới.");
            }
            long remaining = 5 - failedCount;
            throw new BadRequestException("Mã OTP nhập vào không chính xác (Còn lại " + remaining + " lần thử). Vui lòng kiểm tra lại hòm thư.");
        }

        otpBucket.delete();
        attempts.delete();
    }

    private void applyNewPassword(User user, String newPassword) {
        List<PasswordHistory> recentHistories = passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(user.getId());
        for (PasswordHistory history : recentHistories) {
            if (passwordEncoder.matches(newPassword, history.getHashedPassword())) {
                throw new BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất");
            }
        }

        // Lưu mật khẩu hiện tại vào bảng lịch sử nếu user đã có mật khẩu trước đó
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            PasswordHistory passwordHistory = PasswordHistory.builder()
                    .user(user)
                    .hashedPassword(user.getPassword())
                    .build();
            passwordHistoryRepository.save(passwordHistory);
        }

        // Cập nhật mật khẩu mới đã băm
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Thu hồi toàn bộ Refresh Token trên mọi thiết bị
        refreshTokenService.deleteByUserId(user.getId());

        // Gửi email cảnh báo bảo mật
        emailService.sendSecurityAlertEmail(user.getEmail(), user.getFullName(), LocalDateTime.now());
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + userId));
    }
}
