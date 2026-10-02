package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.request.UpdateUserLanguageRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import com.codegym.mathclass.user.service.UserAvatarService;
import com.codegym.mathclass.user.service.UserCredentialService;
import com.codegym.mathclass.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserCredentialService userCredentialService;
    private final UserAvatarService userAvatarService;
    private final RateLimiterService rateLimiterService;
    private final PermissionCacheService permissionCacheService;

    private static final String USER_LAST_ACTIVE_PREFIX = "user:last-active:";

    @Override
    public UserResponse getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));
        List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
        return userMapper.toUserResponse(user, false, permissions);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        userMapper.updateUserFromRequest(user, request);

        userRepository.save(user);
        List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
        return userMapper.toUserResponse(user, false, permissions);
    }

    @Override
    @Transactional
    public String uploadAvatar(Long id, MultipartFile file) {
        return userAvatarService.uploadAvatar(id, file);
    }

    @Override
    @Transactional
    public void updateLastActiveAt(Long userId) {
        if (userId == null)
            return;
        // Throttle DB updates: Chỉ ghi PostgreSQL nếu đã qua hơn 1 phút trên toàn cụm
        // phân tán
        if (rateLimiterService.tryAcquire(USER_LAST_ACTIVE_PREFIX + userId, Duration.ofSeconds(60))) {
            userRepository.updateLastActiveAt(userId, LocalDateTime.now());
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        userCredentialService.changePassword(userId, request);
    }

    @Override
    public void sendSetPasswordOtp(Long userId) {
        userCredentialService.sendSetPasswordOtp(userId);
    }

    @Override
    @Transactional
    public void setPassword(Long userId, SetPasswordRequest request) {
        userCredentialService.setPassword(userId, request);
    }

    @Override
    @Transactional
    public UserResponse updateLanguage(Long userId, UpdateUserLanguageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userId));

        user.setLanguage(request.getLanguage().trim().toLowerCase());
        User updatedUser = userRepository.save(user);
        return userMapper.toUserResponse(updatedUser);
    }
}
