package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.audit.AuthAuditLogger;
import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.auth.service.PasswordRecoveryService;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.service.UserRegistrationService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.auth.strategy.AuthStrategyFactory;
import com.codegym.mathclass.chat.service.UserPresenceRegistry;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.jwt.TokenBlacklistService;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthStrategyFactory authStrategyFactory;

    @Mock
    private AuthAuditLogger authAuditLogger;

    @Mock
    private UserRegistrationService userRegistrationService;

    @Mock
    private PasswordRecoveryService passwordRecoveryService;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserPresenceRegistry userPresenceRegistry;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthStrategy<LoginRequest> localAuthStrategy;

    @Mock
    private AuthStrategy<GoogleAuthRequest> googleAuthStrategy;

    @Mock
    private AuthStrategy<Admin2FaLoginRequest> admin2FaAuthStrategy;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private HttpServletResponse mockResponse;
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("student@test.com")
                .fullName("Test Student")
                .password("encodedPassword")
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        mockUser.setId(1L);

        mockResponse = mock(HttpServletResponse.class);
        mockRequest = mock(HttpServletRequest.class);
    }

    @Nested
    @DisplayName("authenticateUser Tests")
    class AuthenticateUserTests {

        @Test
        @DisplayName("Should delegate to LocalPasswordAuthStrategy and log audit success")
        void authenticateUser_ValidCredentials_Success() {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("student@test.com");
            loginRequest.setPassword("password");

            UserInfoResponse expectedUserInfo = new UserInfoResponse(1L, "student@test.com", "Test Student", "STUDENT", null, List.of());

            when(authAuditLogger.extractClientIp(mockRequest)).thenReturn("127.0.0.1");
            when(authAuditLogger.extractUserAgent(mockRequest)).thenReturn("Mozilla/5.0");
            when(authStrategyFactory.<LoginRequest>getStrategy(AuthType.LOCAL)).thenReturn(localAuthStrategy);
            when(localAuthStrategy.authenticate(loginRequest, mockResponse)).thenReturn(expectedUserInfo);

            UserInfoResponse response = authService.authenticateUser(loginRequest, mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("student@test.com");
            verify(authAuditLogger, times(1)).logSuccess(eq(1L), eq("student@test.com"), eq(AuthType.LOCAL),
                    eq("127.0.0.1"), eq("Mozilla/5.0"));
        }

        @Test
        @DisplayName("Should log audit failure and rethrow exception on strategy error")
        void authenticateUser_StrategyFailure_LogsAndRethrows() {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("student@test.com");
            loginRequest.setPassword("wrong");

            when(authAuditLogger.extractClientIp(mockRequest)).thenReturn("127.0.0.1");
            when(authAuditLogger.extractUserAgent(mockRequest)).thenReturn("Mozilla/5.0");
            when(authStrategyFactory.<LoginRequest>getStrategy(AuthType.LOCAL)).thenReturn(localAuthStrategy);
            when(localAuthStrategy.authenticate(loginRequest, mockResponse))
                    .thenThrow(new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại."));

            assertThatThrownBy(() -> authService.authenticateUser(loginRequest, mockRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email hoặc mật khẩu không đúng");

            verify(authAuditLogger, times(1)).logFailure(eq("student@test.com"), eq(AuthType.LOCAL),
                    anyString(), eq("127.0.0.1"), eq("Mozilla/5.0"));
        }
    }

    @Nested
    @DisplayName("authenticateWithGoogle Tests")
    class AuthenticateWithGoogleTests {

        @Test
        @DisplayName("Should delegate to GoogleOAuth2AuthStrategy and log audit success")
        void authenticateWithGoogle_ValidToken_Success() {
            GoogleAuthRequest googleRequest = new GoogleAuthRequest();
            googleRequest.setCredential("valid-google-id-token");

            UserInfoResponse expectedUserInfo = new UserInfoResponse(2L, "google@test.com", "Google User", "STUDENT", null, List.of());

            when(authAuditLogger.extractClientIp(mockRequest)).thenReturn("127.0.0.1");
            when(authAuditLogger.extractUserAgent(mockRequest)).thenReturn("Mozilla/5.0");
            when(authStrategyFactory.<GoogleAuthRequest>getStrategy(AuthType.GOOGLE)).thenReturn(googleAuthStrategy);
            when(googleAuthStrategy.authenticate(googleRequest, mockResponse)).thenReturn(expectedUserInfo);

            UserInfoResponse response = authService.authenticateWithGoogle(googleRequest, mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("google@test.com");
            verify(authAuditLogger, times(1)).logSuccess(eq(2L), eq("google@test.com"), eq(AuthType.GOOGLE),
                    eq("127.0.0.1"), eq("Mozilla/5.0"));
        }

        @Test
        @DisplayName("Should log audit failure and rethrow on Google auth error")
        void authenticateWithGoogle_StrategyFailure_LogsAndRethrows() {
            GoogleAuthRequest googleRequest = new GoogleAuthRequest();
            googleRequest.setCredential("invalid-token");

            when(authAuditLogger.extractClientIp(mockRequest)).thenReturn("127.0.0.1");
            when(authAuditLogger.extractUserAgent(mockRequest)).thenReturn("Mozilla/5.0");
            when(authStrategyFactory.<GoogleAuthRequest>getStrategy(AuthType.GOOGLE)).thenReturn(googleAuthStrategy);
            when(googleAuthStrategy.authenticate(googleRequest, mockResponse))
                    .thenThrow(new BadRequestException("Token xác thực Google không hợp lệ."));

            assertThatThrownBy(() -> authService.authenticateWithGoogle(googleRequest, mockRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Token xác thực Google không hợp lệ");

            verify(authAuditLogger, times(1)).logFailure(isNull(), eq(AuthType.GOOGLE), anyString(),
                    eq("127.0.0.1"), eq("Mozilla/5.0"));
        }
    }

    @Nested
    @DisplayName("authenticateAdmin2Fa Tests")
    class AuthenticateAdmin2FaTests {

        @Test
        @DisplayName("Should delegate to AdminPortalAuthStrategy and log audit success with IP/Agent")
        void authenticateAdmin2Fa_ValidCredentials_Success() {
            Admin2FaLoginRequest request = new Admin2FaLoginRequest("admin@test.com", "password", "123456");

            UserInfoResponse expectedUserInfo = new UserInfoResponse(3L, "admin@test.com", "Admin User", "ADMIN", null, List.of());

            when(authAuditLogger.extractClientIp(mockRequest)).thenReturn("127.0.0.1");
            when(authAuditLogger.extractUserAgent(mockRequest)).thenReturn("Mozilla/5.0");
            when(authStrategyFactory.<Admin2FaLoginRequest>getStrategy(AuthType.ADMIN_2FA)).thenReturn(admin2FaAuthStrategy);
            when(admin2FaAuthStrategy.authenticate(request, mockResponse)).thenReturn(expectedUserInfo);

            UserInfoResponse response = authService.authenticateAdmin2Fa(request, mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("admin@test.com");
            verify(authAuditLogger, times(1)).logSuccess(eq(3L), eq("admin@test.com"), eq(AuthType.ADMIN_2FA),
                    eq("127.0.0.1"), eq("Mozilla/5.0"));
        }
    }

    @Nested
    @DisplayName("logoutUser Tests")
    class LogoutUserTests {

        @Test
        @DisplayName("Should blacklist token, delete refresh token, update last active, and clear cookies")
        void logoutUser_FullCleanup_Success() {
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L)
                    .token("mock-refresh-token")
                    .user(mockUser)
                    .build();

            when(jwtUtils.getJwtFromCookies(mockRequest)).thenReturn("mock-access-token");
            when(jwtUtils.validateJwtToken("mock-access-token")).thenReturn(true);
            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn("mock-refresh-token");
            when(refreshTokenService.findByToken("mock-refresh-token")).thenReturn(Optional.of(refreshToken));

            MessageResponse response = authService.logoutUser(mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Đăng xuất thành công!");
            verify(tokenBlacklistService, times(1)).blacklistToken("mock-access-token");
            verify(refreshTokenService, times(1)).deleteToken(refreshToken);
            verify(userRepository, times(1)).updateLastActiveAt(eq(1L), any());
            verify(userPresenceRegistry, times(1)).markLoggedOut(1L);
            verify(authSessionService, times(1)).clearSessionCookies(mockResponse);
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

            MessageResponse response = authService.refreshToken(mockRequest, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Token is refreshed successfully!");
            verify(authSessionService).setRefreshAccessCookie(mockUser, mockResponse);
        }

        @Test
        @DisplayName("Should throw BadRequestException and clean cookies if refresh token is revoked or not found")
        void refreshToken_InvalidToken_ThrowsExceptionAndCleansCookies() {
            when(jwtUtils.getJwtRefreshFromCookies(mockRequest)).thenReturn("invalid-refresh-token");
            when(refreshTokenService.findByToken("invalid-refresh-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(mockRequest, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Refresh token không hợp lệ");

            verify(authSessionService).clearSessionCookies(mockResponse);
        }
    }

    @Nested
    @DisplayName("Facade Delegation Tests")
    class FacadeDelegationTests {

        @Test
        @DisplayName("registerUser should delegate to UserRegistrationService")
        void registerUser_Delegates() {
            SignupRequest request = new SignupRequest();
            MessageResponse expected = new MessageResponse("Đăng ký thành công!");
            when(userRegistrationService.registerUser(request)).thenReturn(expected);

            MessageResponse actual = authService.registerUser(request);

            assertThat(actual).isEqualTo(expected);
            verify(userRegistrationService).registerUser(request);
        }

        @Test
        @DisplayName("verifyUser should delegate to UserRegistrationService")
        void verifyUser_Delegates() {
            MessageResponse expected = new MessageResponse("Kích hoạt thành công!");
            when(userRegistrationService.verifyUser("test-token")).thenReturn(expected);

            MessageResponse actual = authService.verifyUser("test-token");

            assertThat(actual).isEqualTo(expected);
            verify(userRegistrationService).verifyUser("test-token");
        }

        @Test
        @DisplayName("forgotPassword should delegate to PasswordRecoveryService")
        void forgotPassword_Delegates() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            MessageResponse expected = new MessageResponse("Đã gửi email!");
            when(passwordRecoveryService.forgotPassword(request)).thenReturn(expected);

            MessageResponse actual = authService.forgotPassword(request);

            assertThat(actual).isEqualTo(expected);
            verify(passwordRecoveryService).forgotPassword(request);
        }

        @Test
        @DisplayName("resetPassword should delegate to PasswordRecoveryService")
        void resetPassword_Delegates() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            MessageResponse expected = new MessageResponse("Mật khẩu đã được cập nhật!");
            when(passwordRecoveryService.resetPassword(request)).thenReturn(expected);

            MessageResponse actual = authService.resetPassword(request);

            assertThat(actual).isEqualTo(expected);
            verify(passwordRecoveryService).resetPassword(request);
        }
    }
}
