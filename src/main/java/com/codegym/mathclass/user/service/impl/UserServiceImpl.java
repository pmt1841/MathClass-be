package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.service.StorageService;
import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.PasswordHistory;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.PasswordHistoryRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.UserService;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiterService rateLimiterService;
    private final RedissonClient redissonClient;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String USER_LAST_ACTIVE_PREFIX = "user:last-active:";
    private static final String SET_PASSWORD_COOLDOWN_PREFIX = "auth:set-password:cooldown:";
    private static final String SET_PASSWORD_OTP_PREFIX = "auth:set-password:otp:";
    private static final String SET_PASSWORD_ATTEMPTS_PREFIX = "auth:set-password:attempts:";

    @Override
    public UserResponse getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));
        UserResponse response = userMapper.toUserResponse(user);
        // Always fetch real-time permissions for UI updates, bypassing the backend auth cache
        response.setPermissions(rolePermissionRepository.findPermissionNamesByRole(user.getRole()));
        return response;
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        userMapper.updateUserFromRequest(user, request);

        userRepository.save(user);
        UserResponse response = userMapper.toUserResponse(user);
        response.setPermissions(rolePermissionRepository.findPermissionNamesByRole(user.getRole()));
        return response;
    }

    @Override
    @Transactional
    public String uploadAvatar(Long id, MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        if (user.getProvider() == Provider.GOOGLE) {
            throw new BadRequestException("Không thể thay đổi ảnh đại diện cho tài khoản liên kết Google");
        }

        String oldAvatarUrl = user.getAvatarUrl();

        try {
            String avatarUrl = storageService.upload(file, StoragePolicy.AVATAR);
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            // Tức thời dọn dẹp avatar cũ nếu là ảnh thuộc hệ thống
            if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
                try {
                    storageService.delete(oldAvatarUrl);
                } catch (Exception e) {
                    // Bắt lỗi an toàn, tránh làm gián đoạn luồng người dùng
                }
            }

            return avatarUrl;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi upload ảnh đại diện: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateLastActiveAt(Long userId) {
        if (userId == null) return;
        // Throttle DB updates: Chỉ ghi PostgreSQL nếu đã qua hơn 1 phút trên toàn cụm phân tán
        if (rateLimiterService.tryAcquire(USER_LAST_ACTIVE_PREFIX + userId, Duration.ofSeconds(60))) {
            userRepository.updateLastActiveAt(userId, LocalDateTime.now());
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + userId));

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

        List<PasswordHistory> recentHistories = passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        for (PasswordHistory history : recentHistories) {
            if (passwordEncoder.matches(request.getNewPassword(), history.getHashedPassword())) {
                throw new BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất");
            }
        }

        // Archive current password hash to history
        PasswordHistory passwordHistory = PasswordHistory.builder()
                .user(user)
                .hashedPassword(user.getPassword())
                .build();
        passwordHistoryRepository.save(passwordHistory);

        // Update user's password with new encoded password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens across devices
        refreshTokenService.deleteByUserId(userId);

        // Send Security Alert Email
        emailService.sendSecurityAlertEmail(user.getEmail(), user.getFullName(), LocalDateTime.now());
    }

    @Override
    public void sendSetPasswordOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + userId));

        if (!rateLimiterService.tryAcquire(SET_PASSWORD_COOLDOWN_PREFIX + userId, Duration.ofSeconds(60))) {
            long remaining = rateLimiterService.getRemainingCooldownSeconds(SET_PASSWORD_COOLDOWN_PREFIX + userId);
            throw new TooManyRequestsException("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau " + remaining + " giây.");
        }

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redissonClient.getBucket(SET_PASSWORD_OTP_PREFIX + userId, StringCodec.INSTANCE).set(otpCode, 5, TimeUnit.MINUTES);
        redissonClient.getAtomicLong(SET_PASSWORD_ATTEMPTS_PREFIX + userId).delete();

        emailService.sendSetPasswordOtpEmail(user.getEmail(), user.getFullName(), otpCode);
    }

    @Override
    @Transactional
    public void setPassword(Long userId, SetPasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + userId));

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không trùng khớp với mật khẩu mới");
        }

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

        if (!savedOtp.equals(request.getOtpCode().trim())) {
            long failedCount = attempts.incrementAndGet();
            attempts.expire(5, TimeUnit.MINUTES);
            if (failedCount >= 5) {
                otpBucket.delete();
                attempts.delete();
                throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy, vui lòng yêu cầu mã mới.");
            }
            long remaining = 5 - failedCount;
            throw new BadRequestException("Mã OTP nhập vào không chính xác (Còn lại " + remaining + " lần thử). Vui lòng kiểm tra lại hòm thư.");
        }

        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại");
            }
        }

        List<PasswordHistory> recentHistories = passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        for (PasswordHistory history : recentHistories) {
            if (passwordEncoder.matches(request.getNewPassword(), history.getHashedPassword())) {
                throw new BadRequestException("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        PasswordHistory passwordHistory = PasswordHistory.builder()
                .user(user)
                .hashedPassword(user.getPassword())
                .build();
        passwordHistoryRepository.save(passwordHistory);

        otpBucket.delete();
        attempts.delete();

        // Revoke all existing refresh tokens across devices
        refreshTokenService.deleteByUserId(userId);

        // Send Security Alert Email
        emailService.sendSecurityAlertEmail(user.getEmail(), user.getFullName(), LocalDateTime.now());
    }
}
