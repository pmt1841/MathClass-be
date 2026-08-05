package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private com.codegym.mathclass.auth.service.RefreshTokenService refreshTokenService;

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

    // ==========================================
    // Tests for getUsersForAdmin
    // ==========================================

    @Nested
    @DisplayName("getUsersForAdmin")
    class GetUsersForAdmin {

        @Test
        @DisplayName("Should return all users when no filters are provided")
        void getUsersForAdmin_NoFilters_ReturnsAllUsers() {
            // Given
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

            // When
            Page<UserResponse> result = adminUserService.getUsersForAdmin(null, null, null, pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("student@test.com");
            verify(userRepository).findAllForAdmin(null, null, null, pageable);
        }

        @Test
        @DisplayName("Should pass null searchParam when search is null")
        void getUsersForAdmin_NullSearch_PassesNullToRepository() {
            // Given
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(null, null, null, pageable);

            // Then – searchParam phải là null, không phải "%null%" hay ""
            verify(userRepository).findAllForAdmin(null, null, null, pageable);
        }

        @Test
        @DisplayName("Should pass null searchParam when search is blank string")
        void getUsersForAdmin_BlankSearch_PassesNullToRepository() {
            // Given
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(null, null, "   ", pageable);

            // Then
            verify(userRepository).findAllForAdmin(null, null, null, pageable);
        }

        @Test
        @DisplayName("Should format search keyword as lowercase LIKE pattern")
        void getUsersForAdmin_WithSearch_PassesLikePatternToRepository() {
            // Given
            String search = "Admin";
            String expectedPattern = "%admin%";
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), eq(expectedPattern), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(null, null, search, pageable);

            // Then
            verify(userRepository).findAllForAdmin(null, null, expectedPattern, pageable);
        }

        @Test
        @DisplayName("Should trim whitespace from search before building pattern")
        void getUsersForAdmin_SearchWithWhitespace_TrimsBeforeBuilding() {
            // Given
            String search = "  admin  ";
            String expectedPattern = "%admin%";
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), eq(expectedPattern), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(null, null, search, pageable);

            // Then
            verify(userRepository).findAllForAdmin(null, null, expectedPattern, pageable);
        }

        @Test
        @DisplayName("Should escape SQL wildcard characters in search keyword")
        void getUsersForAdmin_SearchWithWildcard_EscapesSpecialChars() {
            // Given – user nhập ký tự SQL wildcard
            String search = "50%_off";
            String expectedPattern = "%50\\%\\_off%";
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), isNull(), eq(expectedPattern), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(null, null, search, pageable);

            // Then
            verify(userRepository).findAllForAdmin(null, null, expectedPattern, pageable);
        }

        @Test
        @DisplayName("Should filter by role when role is provided")
        void getUsersForAdmin_WithRole_PassesRoleToRepository() {
            // Given
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(eq(Role.STUDENT), isNull(), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(Role.STUDENT, null, null, pageable);

            // Then
            verify(userRepository).findAllForAdmin(Role.STUDENT, null, null, pageable);
        }

        @Test
        @DisplayName("Should filter by isActive when isActive is provided")
        void getUsersForAdmin_WithIsActive_PassesIsActiveToRepository() {
            // Given
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(isNull(), eq(true), isNull(), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(null, true, null, pageable);

            // Then
            verify(userRepository).findAllForAdmin(null, true, null, pageable);
        }

        @Test
        @DisplayName("Should apply all filters combined")
        void getUsersForAdmin_AllFilters_PassesAllToRepository() {
            // Given
            String expectedPattern = "%student%";
            Page<User> userPage = new PageImpl<>(List.of(mockUser));
            when(userRepository.findAllForAdmin(eq(Role.STUDENT), eq(true), eq(expectedPattern), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(any())).thenReturn(mockUserResponse);

            // When
            adminUserService.getUsersForAdmin(Role.STUDENT, true, "student", pageable);

            // Then
            verify(userRepository).findAllForAdmin(Role.STUDENT, true, expectedPattern, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no users match filters")
        void getUsersForAdmin_NoUsersMatch_ReturnsEmptyPage() {
            // Given
            Page<User> emptyPage = Page.empty(pageable);
            when(userRepository.findAllForAdmin(eq(Role.ADMIN), isNull(), isNull(), eq(pageable))).thenReturn(emptyPage);

            // When
            Page<UserResponse> result = adminUserService.getUsersForAdmin(Role.ADMIN, null, null, pageable);

            // Then
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ==========================================
    // Tests for updateUserStatus
    // ==========================================

    @Nested
    @DisplayName("updateUserStatus")
    class UpdateUserStatus {

        @Test
        @DisplayName("Should deactivate user and log the action")
        void updateUserStatus_DeactivateUser_SavesAndLogs() {
            // Given
            mockUser.setActive(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            // When
            adminUserService.updateUserStatus(1L, false, "admin@test.com");

            // Then
            assertThat(mockUser.isActive()).isFalse();
            verify(userRepository).save(mockUser);
            verify(systemLogService).logInfo(
                eq("admin@test.com"),
                eq("Khóa tài khoản student@test.com (STUDENT)"),
                eq(1L)
            );
        }

        @Test
        @DisplayName("Should activate user and log the action")
        void updateUserStatus_ActivateUser_SavesAndLogs() {
            // Given
            mockUser.setActive(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            // When
            adminUserService.updateUserStatus(1L, true, "admin@test.com");

            // Then
            assertThat(mockUser.isActive()).isTrue();
            verify(userRepository).save(mockUser);
            verify(systemLogService).logInfo(
                eq("admin@test.com"),
                eq("Mở khóa tài khoản student@test.com (STUDENT)"),
                eq(1L)
            );
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void updateUserStatus_UserNotFound_ThrowsException() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminUserService.updateUserStatus(999L, false, "admin@test.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID này.");

            verify(userRepository, never()).save(any());
            verify(systemLogService, never()).logInfo(any(), any(), any());
        }

        @Test
        @DisplayName("Should use ArgumentCaptor to verify log message format")
        void updateUserStatus_VerifyLogActionFormat() {
            // Given
            mockUser.setActive(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);

            // When
            adminUserService.updateUserStatus(1L, true, "admin@test.com");

            // Then
            verify(systemLogService).logInfo(eq("admin@test.com"), actionCaptor.capture(), eq(1L));
            String loggedAction = actionCaptor.getValue();
            assertThat(loggedAction).contains("Mở khóa");
            assertThat(loggedAction).contains("student@test.com");
            assertThat(loggedAction).contains("STUDENT");
        }
    }
}
