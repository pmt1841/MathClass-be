package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.request.UpdateUserLanguageRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import com.codegym.mathclass.user.service.UserAvatarService;
import com.codegym.mathclass.user.service.UserCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserCredentialService userCredentialService;

    @Mock
    private UserAvatarService userAvatarService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;
    private UserResponse mockUserResponse;
    private UpdateProfileRequest mockUpdateRequest;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setFullName("Old Name");
        mockUser.setEmail("user@test.com");
        mockUser.setPassword("encodedOldPassword");
        mockUser.setRole(Role.STUDENT);
        mockUser.setProvider(Provider.LOCAL);

        mockUserResponse = new UserResponse();
        mockUserResponse.setId(1L);
        mockUserResponse.setFullName("New Name");

        mockUpdateRequest = new UpdateProfileRequest();
        mockUpdateRequest.setFullName("New Name");
        mockUpdateRequest.setPhoneNumber("0123456789");
    }

    @Nested
    @DisplayName("getUserProfile Tests")
    class GetUserProfileTests {

        @Test
        @DisplayName("Should return user profile successfully with cached permissions")
        void getUserProfile_success() {
            Long userId = 1L;
            List<String> permissions = List.of("assignment:read");

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(permissions);
            when(userMapper.toUserResponse(mockUser, false, permissions)).thenReturn(mockUserResponse);

            UserResponse result = userService.getUserProfile(userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(userRepository, times(1)).findById(userId);
            verify(permissionCacheService, times(1)).getPermissionsByRole(Role.STUDENT);
        }

        @Test
        @DisplayName("Should throw BadRequestException when user not found")
        void getUserProfile_notFound() {
            Long userId = 99L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserProfile(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: 99");
        }
    }

    @Nested
    @DisplayName("updateProfile Tests")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update user profile and return response")
        void updateProfile_success() {
            Long userId = 1L;
            List<String> permissions = List.of("assignment:read");

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(mockUser)).thenReturn(mockUser);
            when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(permissions);
            when(userMapper.toUserResponse(mockUser, false, permissions)).thenReturn(mockUserResponse);

            UserResponse result = userService.updateProfile(userId, mockUpdateRequest);

            assertThat(result).isNotNull();
            verify(userMapper, times(1)).updateUserFromRequest(mockUser, mockUpdateRequest);
            verify(userRepository, times(1)).save(mockUser);
        }

        @Test
        @DisplayName("Should throw BadRequestException when updating non-existent user")
        void updateProfile_notFound() {
            Long userId = 99L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile(userId, mockUpdateRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: 99");
        }
    }

    @Nested
    @DisplayName("uploadAvatar Delegation Tests")
    class UploadAvatarTests {

        @Test
        @DisplayName("Should delegate uploadAvatar to UserAvatarService")
        void uploadAvatar_delegation() {
            Long userId = 1L;
            MultipartFile mockFile = mock(MultipartFile.class);
            when(userAvatarService.uploadAvatar(userId, mockFile)).thenReturn("https://example.com/avatar.png");

            String result = userService.uploadAvatar(userId, mockFile);

            assertThat(result).isEqualTo("https://example.com/avatar.png");
            verify(userAvatarService, times(1)).uploadAvatar(userId, mockFile);
        }
    }

    @Nested
    @DisplayName("updateLastActiveAt Tests")
    class UpdateLastActiveAtTests {

        @Test
        @DisplayName("Should throttle updateLastActiveAt when rate limit denies")
        void updateLastActiveAt_throttled() {
            Long userId = 1L;
            when(rateLimiterService.tryAcquire(eq("user:last-active:1"), eq(Duration.ofSeconds(60)))).thenReturn(false);

            userService.updateLastActiveAt(userId);

            verify(userRepository, never()).updateLastActiveAt(any(), any());
        }

        @Test
        @DisplayName("Should updateLastActiveAt in DB when rate limit permits")
        void updateLastActiveAt_permitted() {
            Long userId = 1L;
            when(rateLimiterService.tryAcquire(eq("user:last-active:1"), eq(Duration.ofSeconds(60)))).thenReturn(true);

            userService.updateLastActiveAt(userId);

            verify(userRepository, times(1)).updateLastActiveAt(eq(userId), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should do nothing when userId is null")
        void updateLastActiveAt_nullUserId() {
            userService.updateLastActiveAt(null);
            verify(userRepository, never()).updateLastActiveAt(any(), any());
        }
    }

    @Nested
    @DisplayName("Credential Delegation Tests")
    class CredentialDelegationTests {

        @Test
        @DisplayName("Should delegate changePassword to UserCredentialService")
        void changePassword_delegation() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder().build();

            userService.changePassword(userId, request);

            verify(userCredentialService, times(1)).changePassword(userId, request);
        }

        @Test
        @DisplayName("Should delegate sendSetPasswordOtp to UserCredentialService")
        void sendSetPasswordOtp_delegation() {
            Long userId = 1L;

            userService.sendSetPasswordOtp(userId);

            verify(userCredentialService, times(1)).sendSetPasswordOtp(userId);
        }

        @Test
        @DisplayName("Should delegate setPassword to UserCredentialService")
        void setPassword_delegation() {
            Long userId = 1L;
            SetPasswordRequest request = SetPasswordRequest.builder().build();

            userService.setPassword(userId, request);

            verify(userCredentialService, times(1)).setPassword(userId, request);
        }
    }

    @Nested
    @DisplayName("updateLanguage Tests")
    class UpdateLanguageTests {

        @Test
        @DisplayName("Should update language successfully")
        void updateLanguage_success() {
            Long userId = 1L;
            UpdateUserLanguageRequest request = UpdateUserLanguageRequest.builder().language("en").build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(mockUser)).thenReturn(mockUser);
            when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

            UserResponse result = userService.updateLanguage(userId, request);

            assertThat(result).isNotNull();
            assertThat(mockUser.getLanguage()).isEqualTo("en");
            verify(userRepository, times(1)).save(mockUser);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void updateLanguage_notFound() {
            Long userId = 99L;
            UpdateUserLanguageRequest request = UpdateUserLanguageRequest.builder().language("en").build();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateLanguage(userId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: 99");
        }
    }
}
