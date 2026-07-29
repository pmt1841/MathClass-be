package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.BadRequestException;
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
    private final RefreshTokenService refreshTokenService;

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

        /*
         * BẢO MẬT ADMIN: Chặn Admin tự khóa tài khoản của chính mình
         */
        if (user.getEmail().equalsIgnoreCase(currentAdminEmail) && Boolean.FALSE.equals(isActive)) {
            throw new BadRequestException("Bạn không thể tự khóa tài khoản quản trị của chính mình.");
        }

        user.setActive(isActive);
        userRepository.save(user);

        /*
         * VÔ HIỆU HÓA PHIÊN TỨC THÌ:
         * Khi khóa tài khoản (!isActive), xóa toàn bộ RefreshToken của user trong Database
         * để vô hiệu hóa ngay tất cả các phiên làm việc đã đăng nhập trước đó.
         */
        if (Boolean.FALSE.equals(isActive)) {
            refreshTokenService.deleteByUserId(user.getId());
        }

        String action = (isActive ? "Mở khóa" : "Khóa") + " tài khoản " + user.getEmail() + " (" + user.getRole() + ")";
        systemLogService.logInfo(currentAdminEmail, action, user.getId());
    }
}
