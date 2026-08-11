package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.codegym.mathclass.user.dto.request.UpdateUserStatusRequest;

public interface AdminUserService {
    Page<UserResponse> getUsersForAdmin(Role role, Boolean isActive, String search, Pageable pageable);
    void updateUserStatus(Long userId, Boolean isActive, String currentAdminEmail);
    void updateUserStatus(Long userId, UpdateUserStatusRequest request, String currentAdminEmail);
}

