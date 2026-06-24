package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.UserResponse;

public interface UserService {
    UserResponse getUserProfile(Long id);
    UserResponse updateProfile(Long id, com.codegym.mathclass.user.dto.request.UpdateProfileRequest request);
    String uploadAvatar(Long id, org.springframework.web.multipart.MultipartFile file);
}
