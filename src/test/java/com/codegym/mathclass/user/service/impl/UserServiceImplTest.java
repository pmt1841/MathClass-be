package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.request.UpdateUserLanguageRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.storage.service.StorageService;
import com.codegym.mathclass.storage.dto.StoragePolicy;
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
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.utils.EmailService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StorageService storageService;

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

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> otpBucket;

    @Mock
    private RAtomicLong attempts;

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
            when(storageService.upload(mockFile, StoragePolicy.AVATAR)).thenReturn(expectedUrl);

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
            when(storageService.upload(mockFile, StoragePolicy.AVATAR)).thenReturn(expectedUrl);

            String resultUrl = userService.uploadAvatar(userId, mockFile);

            assertThat(resultUrl).isEqualTo(expectedUrl);
            verify(storageService).delete(oldAvatarUrl);
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
            when(storageService.upload(mockFile, StoragePolicy.AVATAR)).thenThrow(new IOException("Upload failed"));

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
            verifyNoInteractions(storageService);
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
        @DisplayName("UT-BE-02: Should send OTP email successfully when rate limit allows")
        void sendSetPasswordOtp_success() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(rateLimiterService.tryAcquire(eq("auth:set-password:cooldown:" + userId), any(Duration.class))).thenReturn(true);
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:" + userId), any(StringCodec.class));
            when(redissonClient.getAtomicLong(eq("auth:set-password:attempts:" + userId))).thenReturn(attempts);

            userService.sendSetPasswordOtp(userId);

            verify(otpBucket, times(1)).set(anyString(), eq(5L), eq(TimeUnit.MINUTES));
            verify(attempts, times(1)).delete();
            verify(emailService, times(1)).sendSetPasswordOtpEmail(eq(mockUser.getEmail()), eq(mockUser.getFullName()), anyString());
        }

        @Test
        @DisplayName("UT-BE-02-B: Should throw TooManyRequestsException when rate limit cooldown active")
        void sendSetPasswordOtp_fail_cooldownActive() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(rateLimiterService.tryAcquire(eq("auth:set-password:cooldown:" + userId), any(Duration.class))).thenReturn(false);
            when(rateLimiterService.getRemainingCooldownSeconds("auth:set-password:cooldown:" + userId)).thenReturn(45L);

            assertThatThrownBy(() -> userService.sendSetPasswordOtp(userId))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau 45 giây.");

            verify(emailService, never()).sendSetPasswordOtpEmail(any(), any(), any());
        }

        @Test
        @DisplayName("UT-BE-03: Should set initial password successfully when valid OTP and new password provided")
        void setPassword_success() {
            Long userId = 1L;
            mockUser.setPassword(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:" + userId), any(StringCodec.class));
            when(otpBucket.get()).thenReturn("123456");
            when(redissonClient.getAtomicLong(eq("auth:set-password:attempts:" + userId))).thenReturn(attempts);
            when(attempts.get()).thenReturn(0L);
            when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");

            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("newPassword123")
                    .confirmPassword("newPassword123")
                    .build();

            userService.setPassword(userId, request);

            assertThat(mockUser.getPassword()).isEqualTo("encodedNewPassword");
            verify(userRepository, times(1)).save(mockUser);
            verify(otpBucket, times(1)).delete();
            verify(attempts, times(1)).delete();
            verify(refreshTokenService, times(1)).deleteByUserId(userId);
            verify(emailService, times(1)).sendSecurityAlertEmail(eq(mockUser.getEmail()), eq(mockUser.getFullName()), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("UT-BE-03-B: Should throw BadRequestException when OTP expired or not sent")
        void setPassword_fail_otpExpired() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:" + userId), any(StringCodec.class));
            when(otpBucket.get()).thenReturn(null);

            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("newPassword123")
                    .confirmPassword("newPassword123")
                    .build();

            assertThatThrownBy(() -> userService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã OTP chưa được gửi hoặc đã hết hạn");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-04: Should throw BadRequestException when invalid OTP code provided")
        void setPassword_fail_invalidOtp() {
            Long userId = 1L;
            mockUser.setPassword(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:" + userId), any(StringCodec.class));
            when(otpBucket.get()).thenReturn("123456");
            when(redissonClient.getAtomicLong(eq("auth:set-password:attempts:" + userId))).thenReturn(attempts);
            when(attempts.get()).thenReturn(0L);
            when(attempts.incrementAndGet()).thenReturn(1L);

            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("999999") // Invalid OTP
                    .newPassword("newPassword123")
                    .confirmPassword("newPassword123")
                    .build();

            assertThatThrownBy(() -> userService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã OTP nhập vào không chính xác (Còn lại 4 lần thử)");

            verify(attempts, times(1)).expire(eq(5L), eq(TimeUnit.MINUTES));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-04-B: Should lock OTP when failed attempts reaches 5")
        void setPassword_fail_maxAttempts() {
            Long userId = 1L;
            mockUser.setPassword(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:" + userId), any(StringCodec.class));
            when(otpBucket.get()).thenReturn("123456");
            when(redissonClient.getAtomicLong(eq("auth:set-password:attempts:" + userId))).thenReturn(attempts);
            when(attempts.get()).thenReturn(5L);

            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("newPassword123")
                    .confirmPassword("newPassword123")
                    .build();

            assertThatThrownBy(() -> userService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Bạn đã nhập sai mã OTP quá 5 lần");

            verify(otpBucket, times(1)).delete();
            verify(attempts, times(1)).delete();
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateLastActiveAt Tests")
    class UpdateLastActiveAtTests {

        @Test
        @DisplayName("Should throttle updateLastActiveAt when rate limit denies")
        void updateLastActiveAt_throttled() {
            Long userId = 1L;
            when(rateLimiterService.tryAcquire(eq("user:last-active:" + userId), any(Duration.class))).thenReturn(false);

            userService.updateLastActiveAt(userId);

            verify(userRepository, never()).updateLastActiveAt(any(), any());
        }

        @Test
        @DisplayName("Should updateLastActiveAt in DB when rate limit permits")
        void updateLastActiveAt_permitted() {
            Long userId = 1L;
            when(rateLimiterService.tryAcquire(eq("user:last-active:" + userId), any(Duration.class))).thenReturn(true);

            userService.updateLastActiveAt(userId);

            verify(userRepository, times(1)).updateLastActiveAt(eq(userId), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("updateLanguage Tests")
    class UpdateLanguageTests {

        @Test
        @DisplayName("User tồn tại và ngôn ngữ hợp lệ -> Cập nhật thành công và trả về UserResponse")
        void updateLanguage_Success() {
            Long userId = 1L;
            UpdateUserLanguageRequest request = new UpdateUserLanguageRequest("en");

            User existingUser = User.builder()
                    .fullName("Nguyễn Văn A")
                    .email("vana@example.com")
                    .language("vi")
                    .role(Role.STUDENT)
                    .build();
            existingUser.setId(userId);

            UserResponse expectedResponse = UserResponse.builder()
                    .id(userId)
                    .fullName("Nguyễn Văn A")
                    .language("en")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userMapper.toUserResponse(any(User.class))).thenReturn(expectedResponse);

            UserResponse actual = userService.updateLanguage(userId, request);

            assertThat(actual).isNotNull();
            assertThat(actual.getLanguage()).isEqualTo("en");
            verify(userRepository, times(1)).save(argThat(u -> "en".equals(u.getLanguage())));
            verify(userMapper, times(1)).toUserResponse(any(User.class));
        }

        @Test
        @DisplayName("User không tồn tại -> Ném ResourceNotFoundException")
        void updateLanguage_UserNotFound_ThrowsException() {
            Long userId = 999L;
            UpdateUserLanguageRequest request = new UpdateUserLanguageRequest("en");

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateLanguage(userId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");

            verify(userRepository, never()).save(any());
        }
    }
}
