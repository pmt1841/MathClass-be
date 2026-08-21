package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.UserResponse;

import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;

public interface UserService {
    UserResponse getUserProfile(Long id);
    UserResponse updateProfile(Long id, UpdateProfileRequest request);
    String uploadAvatar(Long id, MultipartFile file);
    void changePassword(Long userId, ChangePasswordRequest request);
    void sendSetPasswordOtp(Long userId);
    void setPassword(Long userId, SetPasswordRequest request);
}
