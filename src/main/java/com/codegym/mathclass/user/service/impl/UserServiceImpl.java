package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.UserService;
import com.codegym.mathclass.utils.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SupabaseStorageService supabaseStorageService;

    @Override
    public UserResponse getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));
        return userMapper.toUserResponse(user);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserResponse updateProfile(Long id, com.codegym.mathclass.user.dto.request.UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String uploadAvatar(Long id, org.springframework.web.multipart.MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        try {
            String avatarUrl = supabaseStorageService.uploadImage(file, "avatar");
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
            return avatarUrl;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Lỗi khi upload ảnh đại diện: " + e.getMessage());
        }
    }
}
