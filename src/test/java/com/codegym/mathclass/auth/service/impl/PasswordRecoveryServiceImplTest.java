package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.common.ratelimit.RateLimiterService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
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
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> resetBucket;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private PasswordRecoveryServiceImpl passwordRecoveryService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordRecoveryService, "frontendUrl", "http://localhost:3000");

        mockUser = User.builder()
                .email("student@test.com")
                .fullName("Test Student")
                .password("encodedPassword")
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        mockUser.setId(1L);
    }

    @Nested
    @DisplayName("forgotPassword Tests")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should generate token, store in Redis, and send email when email exists")
        void forgotPassword_ValidEmail_Success() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("student@test.com");

            when(rateLimiterService.tryAcquire(eq("auth:forgot:student@test.com"), any(Duration.class))).thenReturn(true);
            when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
            doReturn(resetBucket).when(redissonClient).getBucket(startsWith("auth:reset:"), any(StringCodec.class));

            MessageResponse response = passwordRecoveryService.forgotPassword(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Nếu email của bạn hợp lệ");
            verify(resetBucket, times(1)).set(eq("1"), eq(Duration.ofMinutes(15)));
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@test.com"),
                    eq("Yêu cầu khôi phục mật khẩu MathClass"), eq("forgot-password"), any(Context.class));
        }

        @Test
        @DisplayName("Should return generic success response without sending email if email is not found")
        void forgotPassword_EmailNotFound_ReturnsGenericMessage() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("nonexistent@test.com");

            when(rateLimiterService.tryAcquire(eq("auth:forgot:nonexistent@test.com"), any(Duration.class))).thenReturn(true);
            when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

            MessageResponse response = passwordRecoveryService.forgotPassword(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Nếu email của bạn hợp lệ");
            verify(emailService, never()).sendHtmlMailAsync(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw TooManyRequestsException if request is made again within 60 seconds")
        void forgotPassword_RateLimited_ThrowsException() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("rate@test.com");

            when(rateLimiterService.tryAcquire(eq("auth:forgot:rate@test.com"), any(Duration.class))).thenReturn(false);

            assertThatThrownBy(() -> passwordRecoveryService.forgotPassword(request))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("Bạn đã gửi yêu cầu quá nhanh");
        }
    }

    @Nested
    @DisplayName("resetPassword Tests")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should update password and delete token from Redis when token is valid")
        void resetPassword_ValidToken_Success() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("rawToken123");
            request.setNewPassword("NewPassword123!");

            doReturn(resetBucket).when(redissonClient).getBucket(startsWith("auth:reset:"), any(StringCodec.class));
            when(resetBucket.get()).thenReturn("1");
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(encoder.encode("NewPassword123!")).thenReturn("encodedNewPassword");

            MessageResponse response = passwordRecoveryService.resetPassword(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Mật khẩu của bạn đã được cập nhật thành công");
            assertThat(mockUser.getPassword()).isEqualTo("encodedNewPassword");
            verify(userRepository, times(1)).save(mockUser);
            verify(resetBucket, times(1)).delete();
        }

        @Test
        @DisplayName("Should throw BadRequestException if token is invalid or expired in Redis")
        void resetPassword_InvalidOrExpiredToken_ThrowsException() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("invalidToken");
            request.setNewPassword("NewPassword123!");

            doReturn(resetBucket).when(redissonClient).getBucket(startsWith("auth:reset:"), any(StringCodec.class));
            when(resetBucket.get()).thenReturn(null);

            assertThatThrownBy(() -> passwordRecoveryService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không hợp lệ hoặc đã hết hạn");
        }
    }
}
