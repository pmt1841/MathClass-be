package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.UserService;
import com.codegym.mathclass.utils.SupabaseStorageService;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.entity.PasswordHistory;
import com.codegym.mathclass.user.repository.PasswordHistoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.utils.EmailService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.time.LocalDateTime;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SupabaseStorageService supabaseStorageService;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static class SetPasswordOtpEntry {
        final String otpCode;
        final LocalDateTime expiryTime;
        final LocalDateTime createdAt;
        int failedAttempts;

        SetPasswordOtpEntry(String otpCode, LocalDateTime expiryTime) {
            this.otpCode = otpCode;
            this.expiryTime = expiryTime;
            this.createdAt = LocalDateTime.now();
            this.failedAttempts = 0;
        }
    }

    private final Map<Long, SetPasswordOtpEntry> setPasswordOtpCache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredSetPasswordOtps() {
        LocalDateTime now = LocalDateTime.now();
        setPasswordOtpCache.entrySet().removeIf(entry -> entry.getValue().expiryTime.isBefore(now));
    }

    private final ConcurrentHashMap<Long, LocalDateTime> userLastActiveCache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 600000)
    public void cleanUserLastActiveCache() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        userLastActiveCache.entrySet().removeIf(e -> e.getValue().isBefore(threshold));
    }

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

        try {
            String avatarUrl = supabaseStorageService.uploadImage(file, "avatar");
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
            return avatarUrl;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi upload ảnh đại diện: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateLastActiveAt(Long userId) {
        if (userId == null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUpdated = userLastActiveCache.get(userId);

        // Throttle DB updates: Only write to PostgreSQL if updated > 1 minute ago
        if (lastUpdated == null || lastUpdated.isBefore(now.minusMinutes(1))) {
            userLastActiveCache.put(userId, now);
            userRepository.updateLastActiveAt(userId, now);
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

        SetPasswordOtpEntry existingEntry = setPasswordOtpCache.get(userId);
        if (existingEntry != null && existingEntry.createdAt.plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau 60 giây.");
        }

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        setPasswordOtpCache.put(userId, new SetPasswordOtpEntry(otpCode, LocalDateTime.now().plusMinutes(5)));

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

        SetPasswordOtpEntry otpEntry = setPasswordOtpCache.get(userId);
        if (otpEntry == null || otpEntry.expiryTime.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Mã OTP chưa được gửi hoặc đã hết hạn (hiệu lực 5 phút). Vui lòng bấm 'Gửi mã xác thực' để nhận mã mới.");
        }

        if (otpEntry.failedAttempts >= 5) {
            setPasswordOtpCache.remove(userId);
            throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy, vui lòng yêu cầu mã mới.");
        }

        if (!otpEntry.otpCode.equals(request.getOtpCode().trim())) {
            otpEntry.failedAttempts++;
            if (otpEntry.failedAttempts >= 5) {
                setPasswordOtpCache.remove(userId);
                throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy, vui lòng yêu cầu mã mới.");
            }
            int remaining = 5 - otpEntry.failedAttempts;
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

        setPasswordOtpCache.remove(userId);

        // Revoke all existing refresh tokens across devices
        refreshTokenService.deleteByUserId(userId);

        // Send Security Alert Email
        emailService.sendSecurityAlertEmail(user.getEmail(), user.getFullName(), LocalDateTime.now());
    }
}
