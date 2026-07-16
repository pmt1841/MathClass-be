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
    public Page<UserResponse> getUsersForAdmin(Role role, Boolean isActive, String search, Pageable pageable) {
        return userRepository.findAllForAdmin(role, isActive, search, pageable)
                .map(userMapper::toUserResponse);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Boolean isActive, String currentAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID này."));

        user.setActive(isActive); // Using Lombok generated setter. Usually setActive for boolean
        userRepository.save(user);

        String action = (isActive ? "Mở khóa" : "Khóa") + " tài khoản " + user.getEmail() + " (" + user.getRole() + ")";
        systemLogService.logInfo(currentAdminEmail, action, user.getId());
    }
}
