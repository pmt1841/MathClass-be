package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.UserResponse;

import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    UserResponse getUserProfile(Long id);
    UserResponse updateProfile(Long id, UpdateProfileRequest request);
    String uploadAvatar(Long id, MultipartFile file);
}
