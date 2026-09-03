package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.entity.PasswordHistory;
import com.codegym.mathclass.user.repository.PasswordHistoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.utils.EmailService;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

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
        mockUser.setPassword("encodedOldPassword");
        mockUser.setRole(Role.STUDENT);
        mockUser.setProvider(Provider.LOCAL);

        mockUserResponse = new UserResponse();
        mockUserResponse.setId(1L);
        mockUserResponse.setFullName("New Name");

        mockUpdateRequest = new UpdateProfileRequest();
        mockUpdateRequest.setFullName("New Name");
        mockUpdateRequest.setPhoneNumber("0123456789");
        mockUpdateRequest.setGender(Gender.MALE);
        mockUpdateRequest.setDateOfBirth(LocalDate.of(2000, 1, 1));
        mockUpdateRequest.setAvatarUrl("https://example.com/avatar.png");
    }

    @Nested
    @DisplayName("getUserProfile Tests")
    class GetUserProfileTests {

        @Test
        @DisplayName("Should return user profile when user exists")
        void getUserProfile_UserExists_ReturnsUserResponse() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);
            when(rolePermissionRepository.findPermissionNamesByRole(Role.STUDENT)).thenReturn(List.of("assignment:read"));

            UserResponse result = userService.getUserProfile(userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getPermissions()).contains("assignment:read");
            verify(userRepository, times(1)).findById(userId);
            verify(userMapper, times(1)).toUserResponse(mockUser);
        }

        @Test
        @DisplayName("Should throw BadRequestException when user does not exist")
        void getUserProfile_UserDoesNotExist_ThrowsBadRequestException() {
            Long userId = 99L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserProfile(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: " + userId);

            verify(userRepository, times(1)).findById(userId);
            verify(userMapper, never()).toUserResponse(any());
        }
    }

    @Nested
    @DisplayName("updateProfile Tests")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update profile successfully when user exists")
        void updateProfile_UserExists_UpdatesAndReturnsUserResponse() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);
            when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);
            when(rolePermissionRepository.findPermissionNamesByRole(Role.STUDENT)).thenReturn(List.of("assignment:read"));

            UserResponse result = userService.updateProfile(userId, mockUpdateRequest);

            assertThat(result).isNotNull();
            verify(userRepository, times(1)).save(mockUser);
            verify(userMapper, times(1)).updateUserFromRequest(mockUser, mockUpdateRequest);
        }

        @Test
        @DisplayName("Should throw BadRequestException when user not found on update")
        void updateProfile_UserDoesNotExist_ThrowsBadRequestException() {
            Long userId = 99L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile(userId, mockUpdateRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: " + userId);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("uploadAvatar Tests")
    class UploadAvatarTests {

        @Test
        @DisplayName("Should upload avatar successfully when user exists and storage works")
        void uploadAvatar_UserExistsAndStorageSuccess_ReturnsAvatarUrl() throws IOException {
            Long userId = 1L;
            MultipartFile mockFile = mock(MultipartFile.class);
            String expectedUrl = "https://example.com/new-avatar.png";

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(supabaseStorageService.uploadImage(mockFile, "avatar")).thenReturn(expectedUrl);

            String resultUrl = userService.uploadAvatar(userId, mockFile);

            assertThat(resultUrl).isEqualTo(expectedUrl);
            assertThat(mockUser.getAvatarUrl()).isEqualTo(expectedUrl);
            verify(userRepository, times(1)).save(mockUser);
        }

        @Test
        @DisplayName("Should delete old avatar on Supabase when updating new avatar")
        void uploadAvatar_WithExistingOldAvatar_DeletesOldAvatar() throws IOException {
            Long userId = 1L;
            String oldAvatarUrl = "https://xyz.supabase.co/storage/v1/object/public/avatar/images/old.jpg";
            mockUser.setAvatarUrl(oldAvatarUrl);
            MultipartFile mockFile = mock(MultipartFile.class);
            String expectedUrl = "https://xyz.supabase.co/storage/v1/object/public/avatar/images/new.jpg";

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(supabaseStorageService.uploadImage(mockFile, "avatar")).thenReturn(expectedUrl);

            String resultUrl = userService.uploadAvatar(userId, mockFile);

            assertThat(resultUrl).isEqualTo(expectedUrl);
            verify(supabaseStorageService).deleteImageByUrl(oldAvatarUrl);
        }

        @Test
        @DisplayName("Should throw BadRequestException when user not found on upload avatar")
        void uploadAvatar_UserDoesNotExist_ThrowsBadRequestException() {
            Long userId = 99L;
            MultipartFile mockFile = mock(MultipartFile.class);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.uploadAvatar(userId, mockFile))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: " + userId);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw RuntimeException when storage throws IOException")
        void uploadAvatar_StorageThrowsIOException_ThrowsRuntimeException() throws IOException {
            Long userId = 1L;
            MultipartFile mockFile = mock(MultipartFile.class);

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(supabaseStorageService.uploadImage(mockFile, "avatar")).thenThrow(new IOException("Upload failed"));

            assertThatThrownBy(() -> userService.uploadAvatar(userId, mockFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Lỗi khi upload ảnh đại diện: Upload failed");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when uploading avatar for GOOGLE provider user")
        void uploadAvatar_UserIsGoogle_ThrowsBadRequestException() {
            Long userId = 1L;
            mockUser.setProvider(Provider.GOOGLE);
            MultipartFile mockFile = mock(MultipartFile.class);

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> userService.uploadAvatar(userId, mockFile))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không thể thay đổi ảnh đại diện cho tài khoản liên kết Google");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(supabaseStorageService);
        }
    }

    @Nested
    @DisplayName("changePassword Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("UT-BE-01: Should change password successfully when all criteria are met")
        void changePassword_success_validCredentials() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword("newSecurePass123")
                    .confirmPassword("newSecurePass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("oldPassword123", "encodedOldPassword")).thenReturn(true);
            when(passwordEncoder.matches("newSecurePass123", "encodedOldPassword")).thenReturn(false);
            when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
            when(passwordEncoder.encode("newSecurePass123")).thenReturn("encodedNewPassword");

            userService.changePassword(userId, request);

            assertThat(mockUser.getPassword()).isEqualTo("encodedNewPassword");
            verify(passwordHistoryRepository, times(1)).save(any(PasswordHistory.class));
            verify(userRepository, times(1)).save(mockUser);
        }

        @Test
        @DisplayName("UT-BE-04: Should throw BadRequestException when current password is wrong")
        void changePassword_fail_incorrectCurrentPassword() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("wrongCurrentPass")
                    .newPassword("newSecurePass123")
                    .confirmPassword("newSecurePass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("wrongCurrentPass", "encodedOldPassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu hiện tại không đúng");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-05: Should throw BadRequestException when confirm password does not match new password")
        void changePassword_fail_confirmPasswordMismatch() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword("newSecurePass123")
                    .confirmPassword("mismatchedPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu xác nhận không trùng khớp");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-06: Should throw BadRequestException when new password matches current password")
        void changePassword_fail_sameAsCurrentPassword() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword("oldPassword123")
                    .confirmPassword("oldPassword123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("oldPassword123", "encodedOldPassword")).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu mới không được trùng với mật khẩu hiện tại");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-07/08/09: Should throw BadRequestException when new password matches one of the last 3 passwords")
        void changePassword_fail_matchesPreviousHistoryPassword() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword("pastPassword1")
                    .confirmPassword("pastPassword1")
                    .build();

            PasswordHistory history1 = PasswordHistory.builder().hashedPassword("encodedPast1").build();
            PasswordHistory history2 = PasswordHistory.builder().hashedPassword("encodedPast2").build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("oldPassword123", "encodedOldPassword")).thenReturn(true);
            when(passwordEncoder.matches("pastPassword1", "encodedOldPassword")).thenReturn(false);
            when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(history1, history2));
            when(passwordEncoder.matches("pastPassword1", "encodedPast1")).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-12: Should throw BadRequestException when user password is null/empty")
        void changePassword_fail_userHasNoPassword() {
            Long userId = 1L;
            mockUser.setPassword(null);
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword("newSecurePass123")
                    .confirmPassword("newSecurePass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tài khoản chưa có mật khẩu");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("sendSetPasswordOtp & setPassword Tests")
    class SetPasswordOtpTests {

        @Test
        @DisplayName("UT-BE-02: Should send OTP email successfully when user exists")
        void sendSetPasswordOtp_success() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            userService.sendSetPasswordOtp(userId);

            verify(emailService, times(1)).sendSetPasswordOtpEmail(eq(mockUser.getEmail()), eq(mockUser.getFullName()), anyString());
        }

        @Test
        @DisplayName("UT-BE-03: Should set initial password successfully when valid OTP and new password provided")
        void setPassword_success() {
            Long userId = 1L;
            mockUser.setPassword(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");

            // Step 1: Send OTP to populate internal cache
            userService.sendSetPasswordOtp(userId);

            // Capture OTP sent
            org.mockito.ArgumentCaptor<String> otpCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(emailService).sendSetPasswordOtpEmail(eq(mockUser.getEmail()), eq(mockUser.getFullName()), otpCaptor.capture());
            String capturedOtp = otpCaptor.getValue();

            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode(capturedOtp)
                    .newPassword("newPassword123")
                    .confirmPassword("newPassword123")
                    .build();

            // Step 2: Set password
            userService.setPassword(userId, request);

            assertThat(mockUser.getPassword()).isEqualTo("encodedNewPassword");
            verify(userRepository, times(1)).save(mockUser);
            verify(emailService, times(1)).sendSecurityAlertEmail(eq(mockUser.getEmail()), eq(mockUser.getFullName()), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("UT-BE-04: Should throw BadRequestException when invalid OTP code provided")
        void setPassword_fail_invalidOtp() {
            Long userId = 1L;
            mockUser.setPassword(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            // Send OTP
            userService.sendSetPasswordOtp(userId);

            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("999999") // Invalid OTP
                    .newPassword("newPassword123")
                    .confirmPassword("newPassword123")
                    .build();

            assertThatThrownBy(() -> userService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã OTP nhập vào không chính xác");

            verify(userRepository, never()).save(any());
        }
    }
}
