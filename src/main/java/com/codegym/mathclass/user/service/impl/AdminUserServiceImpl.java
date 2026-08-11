package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.dto.request.UpdateUserStatusRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.LockActionType;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.entity.UserLockHistory;
import com.codegym.mathclass.user.event.UserAccountLockedEvent;
import com.codegym.mathclass.user.event.UserAccountUnlockedEvent;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserLockHistoryRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserLockHistoryRepository userLockHistoryRepository;
    private final UserMapper userMapper;
    private final SystemLogService systemLogService;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersForAdmin(Role role, Boolean isActive, String search, Pageable pageable) {
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
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setIsActive(isActive);
        updateUserStatus(userId, request, currentAdminEmail);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request, String currentAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID này."));

        Boolean targetIsActive = request.getIsActive();

        /*
         * BẢO MẬT ADMIN: Chặn Admin tự khóa tài khoản của chính mình
         */
        if (user.getEmail().equalsIgnoreCase(currentAdminEmail) && Boolean.FALSE.equals(targetIsActive)) {
            throw new BadRequestException("Bạn không thể tự khóa tài khoản quản trị của chính mình.");
        }

        /*
         * VỆ BÀN RACE CONDITION / KHÓA TRÙNG LẶP:
         * Không cho phép khóa một tài khoản đã bị khóa, hoặc mở khóa một tài khoản đang hoạt động.
         */
        if (user.isActive() == Boolean.TRUE.equals(targetIsActive)) {
            throw new BadRequestException(
                    targetIsActive ? "Tài khoản này hiện đang ở trạng thái hoạt động." : "Tài khoản này hiện đang ở trạng thái bị khóa."
            );
        }

        String sanitizedReason = null;
        if (request.getReason() != null && !request.getReason().trim().isEmpty()) {
            sanitizedReason = sanitizeReason(request.getReason());
        }

        if (Boolean.FALSE.equals(targetIsActive)) {
            if (request.getReason() == null || request.getReason().trim().length() < 5) {
                throw new BadRequestException("Lý do khóa tài khoản không được để trống và phải có ít nhất 5 ký tự.");
            }
            user.setLockReason(sanitizedReason);
            user.setLockedAt(LocalDateTime.now());
            user.setLockedBy(currentAdminEmail);
        } else {
            // Khi mở khóa tài khoản (isActive = true), reset lại các trường thông tin khóa trên User entity
            user.setLockReason(null);
            user.setLockedAt(null);
            user.setLockedBy(null);
        }

        user.setActive(targetIsActive);
        userRepository.save(user);

        /*
         * LƯU LỊCH SỬ KHÓA / MỞ KHÓA
         */
        UserLockHistory lockHistory = UserLockHistory.builder()
                .user(user)
                .actionType(targetIsActive ? LockActionType.UNLOCK : LockActionType.LOCK)
                .reason(sanitizedReason)
                .performedBy(currentAdminEmail)
                .build();
        userLockHistoryRepository.save(lockHistory);

        /*
         * VÔ HIỆU HÓA PHIÊN TỨC THÌ & BẮN EVENT EMAIL
         */
        if (Boolean.FALSE.equals(targetIsActive)) {
            refreshTokenService.deleteByUserId(user.getId());
            eventPublisher.publishEvent(new UserAccountLockedEvent(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    sanitizedReason,
                    LocalDateTime.now(),
                    currentAdminEmail
            ));
        } else {
            eventPublisher.publishEvent(new UserAccountUnlockedEvent(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    sanitizedReason,
                    LocalDateTime.now(),
                    currentAdminEmail
            ));
        }

        String action = (targetIsActive ? "Mở khóa" : "Khóa") + " tài khoản " + user.getEmail() + " (" + user.getRole() + ")";

        systemLogService.logInfo(currentAdminEmail, action, user.getId());
    }

    private String sanitizeReason(String reason) {
        if (reason == null) return null;
        return reason.trim()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

