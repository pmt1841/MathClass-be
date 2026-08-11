package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.dto.request.UpdateUserStatusRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.event.UserAccountLockedEvent;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserLockHistoryRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLockHistoryRepository userLockHistoryRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private User mockUser;
    private UserResponse mockUserResponse;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("student@test.com");
        mockUser.setFullName("Student A");
        mockUser.setRole(Role.STUDENT);
        mockUser.setActive(true);
        mockUser.setProvider(Provider.LOCAL);

        mockUserResponse = new UserResponse();
        mockUserResponse.setId(1L);
        mockUserResponse.setEmail("student@test.com");
        mockUserResponse.setFullName("Student A");
        mockUserResponse.setRole(Role.STUDENT);

        pageable = PageRequest.of(0, 10);
    }

    @Nested
    @DisplayName("getUsersForAdmin")
    class GetUsersForAdmin {

        @Test
        @DisplayName("Should return all users when no filters are provided")
        void getUsersForAdmin_NoFilters_ReturnsAllUsers() {
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

            Page<UserResponse> result = adminUserService.getUsersForAdmin(null, null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("student@test.com");
            verify(userRepository).findAllForAdmin(null, null, null, pageable);
        }

        @Test
        @DisplayName("Should filter by role when role is provided")
        void getUsersForAdmin_WithRole_PassesRoleToRepository() {
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(eq(Role.STUDENT), isNull(), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            adminUserService.getUsersForAdmin(Role.STUDENT, null, null, pageable);

            verify(userRepository).findAllForAdmin(Role.STUDENT, null, null, pageable);
        }
    }

    @Nested
    @DisplayName("updateUserStatus")
    class UpdateUserStatus {

        @Test
        @DisplayName("Should deactivate user with reason, save history and publish event")
        void updateUserStatus_DeactivateUser_SavesAndPublishesEvent() {
            mockUser.setActive(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(false);
            request.setReason("Vi phạm quy định nộp bài tập");

            adminUserService.updateUserStatus(1L, request, "admin@test.com");

            assertThat(mockUser.isActive()).isFalse();
            assertThat(mockUser.getLockReason()).isEqualTo("Vi phạm quy định nộp bài tập");
            assertThat(mockUser.getLockedBy()).isEqualTo("admin@test.com");
            assertThat(mockUser.getLockedAt()).isNotNull();

            verify(userRepository).save(mockUser);
            verify(userLockHistoryRepository).save(any());
            verify(refreshTokenService).deleteByUserId(1L);
            verify(eventPublisher).publishEvent(any(UserAccountLockedEvent.class));
            verify(systemLogService).logInfo(
                eq("admin@test.com"),
                eq("Khóa tài khoản student@test.com (STUDENT)"),
                eq(1L)
            );
        }

        @Test
        @DisplayName("Should activate user and save history without email event")
        void updateUserStatus_ActivateUser_SavesHistory() {
            mockUser.setActive(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(true);

            adminUserService.updateUserStatus(1L, request, "admin@test.com");

            assertThat(mockUser.isActive()).isTrue();
            verify(userRepository).save(mockUser);
            verify(userLockHistoryRepository).save(any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(systemLogService).logInfo(
                eq("admin@test.com"),
                eq("Mở khóa tài khoản student@test.com (STUDENT)"),
                eq(1L)
            );
        }

        @Test
        @DisplayName("Should throw BadRequestException when locking user without reason or reason too short")
        void updateUserStatus_LockWithoutReason_ThrowsException() {
            mockUser.setActive(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(false);
            request.setReason("123"); // Too short (< 5 chars)

            assertThatThrownBy(() -> adminUserService.updateUserStatus(1L, request, "admin@test.com"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Lý do khóa tài khoản không được để trống và phải có ít nhất 5 ký tự.");
        }

        @Test
        @DisplayName("Should throw BadRequestException when user status is already identical")
        void updateUserStatus_AlreadyLocked_ThrowsException() {
            mockUser.setActive(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(false);
            request.setReason("Vi phạm quy định");

            assertThatThrownBy(() -> adminUserService.updateUserStatus(1L, request, "admin@test.com"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tài khoản này hiện đang ở trạng thái bị khóa.");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void updateUserStatus_UserNotFound_ThrowsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(false);
            request.setReason("Lý do khóa tài khoản");

            assertThatThrownBy(() -> adminUserService.updateUserStatus(999L, request, "admin@test.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID này.");

            verify(userRepository, never()).save(any());
            verify(systemLogService, never()).logInfo(any(), any(), any());
        }
    }
}
