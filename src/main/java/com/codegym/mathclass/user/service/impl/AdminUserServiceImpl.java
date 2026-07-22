package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SystemLogService systemLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersForAdmin(Role role, Boolean isActive, String search, Pageable pageable) {
        // Build LIKE pattern trong Java thay vì CONCAT() bên trong JPQL,
        // để tránh lỗi "function lower(bytea) does not exist" trên PostgreSQL
        // khi Hibernate truyền tham số null vào biểu thức chuỗi trong câu SQL.
        // Đồng thời escape ký tự wildcard SQL (%, _) để ngăn người dùng
        // vô tình làm lệch kết quả tìm kiếm.
        String searchParam = null;
        if (search != null && !search.trim().isEmpty()) {
            String sanitized = search.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            searchParam = "%" + sanitized.toLowerCase() + "%";
        }
        return userRepository.findAllForAdmin(role, isActive, searchParam, pageable)
                .map(userMapper::toUserResponse);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Boolean isActive, String currentAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID này."));

        user.setActive(isActive);
        userRepository.save(user);

        String action = (isActive ? "Mở khóa" : "Khóa") + " tài khoản " + user.getEmail() + " (" + user.getRole() + ")";
        systemLogService.logInfo(currentAdminEmail, action, user.getId());
    }
}
