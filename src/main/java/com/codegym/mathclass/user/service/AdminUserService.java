package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    Page<UserResponse> getUsersForAdmin(Role role, Boolean isActive, String search, Pageable pageable);
    void updateUserStatus(Long userId, Boolean isActive, String currentAdminEmail);
}
