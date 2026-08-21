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
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SupabaseStorageService supabaseStorageService;
    private final RolePermissionRepository rolePermissionRepository;

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
        userRepository.updateLastActiveAtIfOlderThan(
                userId,
                java.time.LocalDateTime.now().minusMinutes(1),
                java.time.LocalDateTime.now()
        );
    }
}
