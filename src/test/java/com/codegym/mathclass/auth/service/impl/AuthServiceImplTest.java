package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.request.*;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.PasswordResetToken;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.repository.PasswordResetTokenRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import com.codegym.mathclass.utils.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private CustomUserDetails mockUserDetails;
    private HttpServletResponse mockResponse;
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");

        mockUser = User.builder()
                .email("student@test.com")
                .fullName("Test Student")
                .password("encodedPassword")
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        mockUser.setId(1L);

        mockUserDetails = new CustomUserDetails(
                1L,
                "Test Student",
                "student@test.com",
                "encodedPassword",
                true,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockResponse = mock(HttpServletResponse.class);
        mockRequest = mock(HttpServletRequest.class);
    }

    @Nested
    @DisplayName("authenticateUser Tests")
    class AuthenticateUserTests {

        @Test
        @DisplayName("Should authenticate user and set cookies when credentials and role match")
        void authenticateUser_ValidCredentials_Success() {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("student@test.com");
            loginRequest.setPassword("password");
            loginRequest.setRole("STUDENT");

            Authentication authentication = mock(Authentication.class);
            RefreshToken mockRefreshToken = RefreshToken.builder()
                    .id(1L)
                    .token("refresh-token-uuid")
                    .user(mockUser)
                    .expiryDate(Instant.now().plusSeconds(86400))
                    .build();

            UserInfoResponse expectedUserInfo = new UserInfoResponse(1L, "student@test.com", "Test Student", "STUDENT", null, List.of());

            when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(jwtUtils.generateJwtCookie(eq(mockUserDetails), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_jwt", "jwt-token").build());
            when(jwtUtils.generateJwtToken(authentication)).thenReturn("jwt-token");
            when(refreshTokenService.createRefreshToken(1L)).thenReturn(mockRefreshToken);
            when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_refresh", "refresh-token-uuid").build());
            when(userMapper.toUserInfoResponse(eq(mockUserDetails), anyString())).thenReturn(expectedUserInfo);

            UserInfoResponse response = authService.authenticateUser(loginRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("student@test.com");
            assertThat(response.getUserRole()).isEqualTo("STUDENT");
            verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
        }

        @Test
        @DisplayName("Should throw BadRequestException when email is not found")
        void authenticateUser_EmailNotFound_ThrowsException() {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("notfound@test.com");
            loginRequest.setPassword("password");

            when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.authenticateUser(loginRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email hoặc mật khẩu không đúng");
        }

        @Test
        @DisplayName("Should throw BadRequestException when STUDENT attempts login to TEACHER portal")
        void authenticateUser_StudentTriesTeacherPortal_ThrowsException() {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("student@test.com");
            loginRequest.setPassword("password");
            loginRequest.setRole("TEACHER");

            when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.authenticateUser(loginRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email hoặc mật khẩu không đúng");
        }

        @Test
        @DisplayName("Should throw BadRequestException when TEACHER attempts login to STUDENT portal")
        void authenticateUser_TeacherTriesStudentPortal_ThrowsException() {
            User teacherUser = User.builder().email("teacher@test.com").role(Role.TEACHER).build();
            teacherUser.setId(2L);

            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("teacher@test.com");
            loginRequest.setPassword("password");
            loginRequest.setRole("STUDENT");

            when(userRepository.findByEmail("teacher@test.com")).thenReturn(Optional.of(teacherUser));

            assertThatThrownBy(() -> authService.authenticateUser(loginRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email hoặc mật khẩu không đúng");
        }

        @Test
        @DisplayName("Should throw BadRequestException when password is incorrect (BadCredentialsException)")
        void authenticateUser_BadCredentials_ThrowsException() {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("student@test.com");
            loginRequest.setPassword("wrongpassword");

            when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.authenticateUser(loginRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email hoặc mật khẩu không đúng");
        }
    }

    @Nested
    @DisplayName("registerUser Tests")
    class RegisterUserTests {

        @Test
        @DisplayName("Should register new user and send verification email")
        void registerUser_ValidRequest_Success() {
            SignupRequest signupRequest = new SignupRequest();
            signupRequest.setEmail("newuser@test.com");
            signupRequest.setPassword("Password123");
            signupRequest.setFullName("New User");
            signupRequest.setPhoneNumber("0987654321");
            signupRequest.setRole(Role.STUDENT);

            when(userRepository.existsByEmail("newuser@test.com")).thenReturn(false);
            when(encoder.encode("Password123")).thenReturn("encodedPassword");

            MessageResponse response = authService.registerUser(signupRequest);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Đăng ký tài khoản thành công");
            verify(userRepository, times(1)).save(any(User.class));
            verify(notificationSettingsRepository, times(1)).save(any(NotificationSettings.class));
            verify(emailService, times(1)).sendHtmlMailAsync(eq("newuser@test.com"), eq("Xác nhận đăng ký tài khoản MathClass"), eq("auth-verify"), any(Context.class));
        }

        @Test
        @DisplayName("Should throw BadRequestException if email already exists")
        void registerUser_ExistingEmail_ThrowsException() {
            SignupRequest signupRequest = new SignupRequest();
            signupRequest.setEmail("student@test.com");

            when(userRepository.existsByEmail("student@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerUser(signupRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Lỗi: Email đã tồn tại!");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException if registration role is ADMIN")
        void registerUser_AdminRole_ThrowsException() {
            SignupRequest signupRequest = new SignupRequest();
            signupRequest.setEmail("admin@test.com");
            signupRequest.setRole(Role.ADMIN);

            when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);

            assertThatThrownBy(() -> authService.registerUser(signupRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Lỗi đăng ký tài khoản");
        }
    }

    @Nested
    @DisplayName("verifyUser Tests")
    class VerifyUserTests {

        @Test
        @DisplayName("Should activate user and send welcome email for valid token")
        void verifyUser_ValidToken_Success() {
            String token = "valid-uuid-token";
            mockUser.setVerificationCode(token);
            mockUser.setActive(false);

            when(userRepository.findByVerificationCode(token)).thenReturn(Optional.of(mockUser));

            MessageResponse response = authService.verifyUser(token);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Tài khoản đã được kích hoạt thành công!");
            assertThat(mockUser.isActive()).isTrue();
            assertThat(mockUser.getVerificationCode()).isNull();
            verify(userRepository, times(1)).save(mockUser);
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@test.com"), eq("Kích hoạt tài khoản thành công"), eq("auth-welcome"), any(Context.class));
        }

        @Test
        @DisplayName("Should throw BadRequestException when token is invalid")
        void verifyUser_InvalidToken_ThrowsException() {
            when(userRepository.findByVerificationCode("invalid-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyUser("invalid-token"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã xác nhận không hợp lệ!");
        }
    }

    @Nested
    @DisplayName("forgotPassword Tests")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should create reset token and send email for valid user email")
        void forgotPassword_ValidUser_Success() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("student@test.com");

            when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
            when(passwordResetTokenRepository.findByUserAndIsUsedFalse(mockUser)).thenReturn(Optional.empty());

            MessageResponse response = authService.forgotPassword(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Nếu email của bạn hợp lệ");
            verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@test.com"), eq("Yêu cầu khôi phục mật khẩu MathClass"), eq("forgot-password"), any(Context.class));
        }

        @Test
        @DisplayName("Should return generic success response without sending email if email is not found")
        void forgotPassword_EmailNotFound_ReturnsGenericMessage() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("nonexistent@test.com");

            when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

            MessageResponse response = authService.forgotPassword(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Nếu email của bạn hợp lệ");
            verify(emailService, never()).sendHtmlMailAsync(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw TooManyRequestsException if request is made again within 60 seconds")
        void forgotPassword_RateLimited_ThrowsException() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("rate@test.com");

            when(userRepository.findByEmail("rate@test.com")).thenReturn(Optional.of(mockUser));

            // First call -> Success
            authService.forgotPassword(request);

            // Second immediate call -> Rate limited
            assertThatThrownBy(() -> authService.forgotPassword(request))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("Bạn đã gửi yêu cầu quá nhanh");
        }
    }

    @Nested
    @DisplayName("resetPassword Tests")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should update password and mark token as used when token is valid")
        void resetPassword_ValidToken_Success() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("rawToken123");
            request.setNewPassword("NewPassword123!");

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(mockUser)
                    .tokenHash("hash")
                    .expiryDate(LocalDateTime.now().plusMinutes(10))
                    .isUsed(false)
                    .build();
            resetToken.setId(1L);

            when(passwordResetTokenRepository.findByTokenHashAndIsUsedFalse(anyString())).thenReturn(Optional.of(resetToken));
            when(encoder.encode("NewPassword123!")).thenReturn("encodedNewPassword");

            MessageResponse response = authService.resetPassword(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Mật khẩu của bạn đã được cập nhật thành công");
            assertThat(resetToken.isUsed()).isTrue();
            assertThat(mockUser.getPassword()).isEqualTo("encodedNewPassword");
            verify(userRepository, times(1)).save(mockUser);
            verify(passwordResetTokenRepository, times(1)).save(resetToken);
        }

        @Test
        @DisplayName("Should throw BadRequestException if token hash is invalid or used")
        void resetPassword_InvalidOrUsedToken_ThrowsException() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("invalidToken");
            request.setNewPassword("NewPassword123!");

            when(passwordResetTokenRepository.findByTokenHashAndIsUsedFalse(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Token không hợp lệ hoặc đã qua sử dụng");
        }

        @Test
        @DisplayName("Should throw BadRequestException if token is expired")
        void resetPassword_ExpiredToken_ThrowsException() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("expiredToken");
            request.setNewPassword("NewPassword123!");

            PasswordResetToken expiredResetToken = PasswordResetToken.builder()
                    .user(mockUser)
                    .tokenHash("hash")
                    .expiryDate(LocalDateTime.now().minusMinutes(5))
                    .isUsed(false)
                    .build();
            expiredResetToken.setId(1L);

            when(passwordResetTokenRepository.findByTokenHashAndIsUsedFalse(anyString())).thenReturn(Optional.of(expiredResetToken));

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Đường dẫn đặt lại mật khẩu đã hết hạn");
        }
    }

    @Nested
    @DisplayName("refreshToken Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should generate new JWT cookie when refresh token cookie is valid")
        void refreshToken_ValidCookie_Success() {
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L)
                    .token("valid-refresh-token")
                    .user(mockUser)
                    .expiryDate(Instant.now().plusSeconds(3600))
                    .build();

            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn("valid-refresh-token");
            when(refreshTokenService.findByToken("valid-refresh-token")).thenReturn(Optional.of(refreshToken));
            when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
            when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(List.of("READ_COURSE"));
            when(jwtUtils.generateJwtCookie(any(CustomUserDetails.class))).thenReturn(ResponseCookie.from("mathclass_jwt", "new-jwt-token").build());

            MessageResponse response = authService.refreshToken(mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Token is refreshed successfully!");
            verify(mockResponse, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
        }

        @Test
        @DisplayName("Should throw BadRequestException when refresh token cookie is empty")
        void refreshToken_EmptyCookie_ThrowsException() {
            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn("");

            assertThatThrownBy(() -> authService.refreshToken(mockRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Refresh Token bị trống!");
        }

        @Test
        @DisplayName("Should throw BadRequestException when refresh token is not found")
        void refreshToken_TokenNotFound_ThrowsException() {
            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn("not-found-token");
            when(refreshTokenService.findByToken("not-found-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(mockRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Refresh token không hợp lệ hoặc đã bị thu hồi!");
        }
    }

    @Nested
    @DisplayName("logoutUser Tests")
    class LogoutUserTests {

        @Test
        @DisplayName("Should delete refresh token and clear cookies on logout")
        void logoutUser_WithRefreshToken_DeletesTokenAndClearsCookies() {
            RefreshToken refreshToken = RefreshToken.builder().id(1L).user(mockUser).build();

            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn("cookie-token");
            when(refreshTokenService.findByToken("cookie-token")).thenReturn(Optional.of(refreshToken));
            when(jwtUtils.getCleanJwtCookie()).thenReturn(ResponseCookie.from("mathclass_jwt", "").build());
            when(jwtUtils.getCleanJwtRefreshCookie()).thenReturn(ResponseCookie.from("mathclass_refresh", "").build());

            MessageResponse response = authService.logoutUser(mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Đăng xuất thành công!");

            verify(refreshTokenService, times(1)).deleteToken(refreshToken);
            verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
        }

        @Test
        @DisplayName("Should return success message even if refresh token cookie is null")
        void logoutUser_WithoutRefreshToken_ClearsCookies() {
            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn(null);
            when(jwtUtils.getCleanJwtCookie()).thenReturn(ResponseCookie.from("mathclass_jwt", "").build());
            when(jwtUtils.getCleanJwtRefreshCookie()).thenReturn(ResponseCookie.from("mathclass_refresh", "").build());

            MessageResponse response = authService.logoutUser(mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Đăng xuất thành công!");

            verify(refreshTokenService, never()).deleteToken(any());
            verify(refreshTokenService, never()).deleteByUserId(any());
            verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
        }
    }
}
