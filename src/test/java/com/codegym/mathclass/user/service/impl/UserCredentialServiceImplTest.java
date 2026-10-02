package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;
import com.codegym.mathclass.user.entity.PasswordHistory;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.PasswordHistoryRepository;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> otpBucket;

    @Mock
    private RAtomicLong attempts;

    @InjectMocks
    private UserCredentialServiceImpl credentialService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        mockUser.setFullName("User Test");
        mockUser.setPassword("encodedOldPassword");
        mockUser.setRole(Role.STUDENT);
        mockUser.setProvider(Provider.LOCAL);
    }

    @Nested
    @DisplayName("changePassword Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password successfully when all criteria are met")
        void changePassword_success() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPass123")
                    .newPassword("newPass123")
                    .confirmPassword("newPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("oldPass123", "encodedOldPassword")).thenReturn(true);
            when(passwordEncoder.matches("newPass123", "encodedOldPassword")).thenReturn(false);
            when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
            when(passwordEncoder.encode("newPass123")).thenReturn("encodedNewPassword");

            credentialService.changePassword(userId, request);

            assertThat(mockUser.getPassword()).isEqualTo("encodedNewPassword");
            verify(passwordHistoryRepository, times(1)).save(any(PasswordHistory.class));
            verify(userRepository, times(1)).save(mockUser);
            verify(refreshTokenService, times(1)).deleteByUserId(userId);
            verify(emailService, times(1)).sendSecurityAlertEmail(eq("user@test.com"), eq("User Test"), any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when user not found")
        void changePassword_fail_userNotFound() {
            Long userId = 99L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPass123")
                    .newPassword("newPass123")
                    .confirmPassword("newPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> credentialService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy người dùng với ID: 99");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when user has no existing password")
        void changePassword_fail_noExistingPassword() {
            Long userId = 1L;
            mockUser.setPassword(null);
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPass123")
                    .newPassword("newPass123")
                    .confirmPassword("newPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> credentialService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tài khoản chưa có mật khẩu");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when confirmPassword mismatch")
        void changePassword_fail_confirmPasswordMismatch() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPass123")
                    .newPassword("newPass123")
                    .confirmPassword("mismatch123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> credentialService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu xác nhận không trùng khớp");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when current password is wrong")
        void changePassword_fail_wrongCurrentPassword() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("wrongPass123")
                    .newPassword("newPass123")
                    .confirmPassword("newPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("wrongPass123", "encodedOldPassword")).thenReturn(false);

            assertThatThrownBy(() -> credentialService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu hiện tại không đúng");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when new password matches current password")
        void changePassword_fail_newMatchesCurrent() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPass123")
                    .newPassword("oldPass123")
                    .confirmPassword("oldPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("oldPass123", "encodedOldPassword")).thenReturn(true);

            assertThatThrownBy(() -> credentialService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu mới không được trùng với mật khẩu hiện tại");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when new password matches one of top 3 recent history")
        void changePassword_fail_matchesRecentHistory() {
            Long userId = 1L;
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("oldPass123")
                    .newPassword("historyPass123")
                    .confirmPassword("historyPass123")
                    .build();

            PasswordHistory history1 = PasswordHistory.builder().hashedPassword("hash1").build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("oldPass123", "encodedOldPassword")).thenReturn(true);
            when(passwordEncoder.matches("historyPass123", "encodedOldPassword")).thenReturn(false);
            when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(history1));
            when(passwordEncoder.matches("historyPass123", "hash1")).thenReturn(true);

            assertThatThrownBy(() -> credentialService.changePassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu mới không được trùng với 3 mật khẩu gần nhất");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("sendSetPasswordOtp Tests")
    class SendSetPasswordOtpTests {

        @Test
        @DisplayName("Should send OTP successfully when rate limiter permits")
        void sendSetPasswordOtp_success() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(rateLimiterService.tryAcquire(eq("auth:set-password:cooldown:1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:1"), eq(StringCodec.INSTANCE));
            when(redissonClient.getAtomicLong("auth:set-password:attempts:1")).thenReturn(attempts);

            credentialService.sendSetPasswordOtp(userId);

            verify(otpBucket, times(1)).set(anyString(), eq(Duration.ofMinutes(5)));
            verify(attempts, times(1)).delete();
            verify(emailService, times(1)).sendSetPasswordOtpEmail(eq("user@test.com"), eq("User Test"), anyString());
        }

        @Test
        @DisplayName("Should throw TooManyRequestsException when rate limit is active")
        void sendSetPasswordOtp_rateLimited() {
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(rateLimiterService.tryAcquire(eq("auth:set-password:cooldown:1"), eq(Duration.ofSeconds(60)))).thenReturn(false);
            when(rateLimiterService.getRemainingCooldownSeconds("auth:set-password:cooldown:1")).thenReturn(45L);

            assertThatThrownBy(() -> credentialService.sendSetPasswordOtp(userId))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("45 giây");

            verify(emailService, never()).sendSetPasswordOtpEmail(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("setPassword Tests")
    class SetPasswordTests {

        @Test
        @DisplayName("Should set password successfully with valid OTP and criteria")
        void setPassword_success() {
            Long userId = 1L;
            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("brandNewPass123")
                    .confirmPassword("brandNewPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:1"), eq(StringCodec.INSTANCE));
            when(otpBucket.get()).thenReturn("123456");
            when(redissonClient.getAtomicLong("auth:set-password:attempts:1")).thenReturn(attempts);
            when(attempts.get()).thenReturn(0L);
            when(passwordEncoder.matches("brandNewPass123", "encodedOldPassword")).thenReturn(false);
            when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
            when(passwordEncoder.encode("brandNewPass123")).thenReturn("encodedBrandNewPass");

            credentialService.setPassword(userId, request);

            assertThat(mockUser.getPassword()).isEqualTo("encodedBrandNewPass");
            verify(otpBucket, times(1)).delete();
            verify(attempts, times(1)).delete();
            verify(refreshTokenService, times(1)).deleteByUserId(userId);
            verify(emailService, times(1)).sendSecurityAlertEmail(eq("user@test.com"), eq("User Test"), any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when confirmPassword mismatch")
        void setPassword_fail_confirmMismatch() {
            Long userId = 1L;
            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("brandNewPass123")
                    .confirmPassword("mismatchPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> credentialService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mật khẩu xác nhận không trùng khớp");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when OTP is missing or expired")
        void setPassword_fail_otpExpired() {
            Long userId = 1L;
            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("brandNewPass123")
                    .confirmPassword("brandNewPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:1"), eq(StringCodec.INSTANCE));
            when(otpBucket.get()).thenReturn(null);

            assertThatThrownBy(() -> credentialService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã OTP chưa được gửi hoặc đã hết hạn");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when OTP attempts exceed 5")
        void setPassword_fail_otpAttemptsExceeded() {
            Long userId = 1L;
            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("123456")
                    .newPassword("brandNewPass123")
                    .confirmPassword("brandNewPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:1"), eq(StringCodec.INSTANCE));
            when(otpBucket.get()).thenReturn("654321");
            when(redissonClient.getAtomicLong("auth:set-password:attempts:1")).thenReturn(attempts);
            when(attempts.get()).thenReturn(5L);

            assertThatThrownBy(() -> credentialService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Bạn đã nhập sai mã OTP quá 5 lần");

            verify(otpBucket, times(1)).delete();
            verify(attempts, times(1)).delete();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when OTP is incorrect")
        void setPassword_fail_otpIncorrect() {
            Long userId = 1L;
            SetPasswordRequest request = SetPasswordRequest.builder()
                    .otpCode("000000")
                    .newPassword("brandNewPass123")
                    .confirmPassword("brandNewPass123")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            doReturn(otpBucket).when(redissonClient).getBucket(eq("auth:set-password:otp:1"), eq(StringCodec.INSTANCE));
            when(otpBucket.get()).thenReturn("123456");
            when(redissonClient.getAtomicLong("auth:set-password:attempts:1")).thenReturn(attempts);
            when(attempts.get()).thenReturn(0L);
            when(attempts.incrementAndGet()).thenReturn(1L);

            assertThatThrownBy(() -> credentialService.setPassword(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã OTP nhập vào không chính xác (Còn lại 4 lần thử)");

            verify(attempts, times(1)).expire(Duration.ofMinutes(5));
            verify(userRepository, never()).save(any());
        }
    }
}
