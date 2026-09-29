package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalPasswordAuthStrategyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private UserTwoFactorAuthRepository userTwoFactorAuthRepository;

    @InjectMocks
    private LocalPasswordAuthStrategy strategy;

    private User mockUser;
    private HttpServletResponse mockResponse;

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
    }

    @Test
    @DisplayName("Should return true when supports AuthType.LOCAL")
    void supports_LocalAuthType_ReturnsTrue() {
        assertThat(strategy.supports(AuthType.LOCAL)).isTrue();
        assertThat(strategy.supports(AuthType.GOOGLE)).isFalse();
        assertThat(strategy.supports(AuthType.ADMIN_2FA)).isFalse();
    }

    @Test
    @DisplayName("Should authenticate user and delegate to AuthSessionService when credentials are valid")
    void authenticate_ValidCredentials_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("password");

        Authentication authentication = mock(Authentication.class);
        UserInfoResponse expectedUserInfo = new UserInfoResponse(1L, "student@test.com", "Test Student", "STUDENT", null, List.of());

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authSessionService.issueAuthSession(mockUser, false, mockResponse)).thenReturn(expectedUserInfo);

        UserInfoResponse response = strategy.authenticate(request, mockResponse);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("student@test.com");
        verify(authSessionService).issueAuthSession(mockUser, false, mockResponse);
    }

    @Test
    @DisplayName("Should throw BadRequestException when user email is not found")
    void authenticate_EmailNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("notfound@test.com");
        request.setPassword("password");

        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");
    }

    @Test
    @DisplayName("Should throw BadRequestException when user account is inactive")
    void authenticate_InactiveUser_ThrowsException() {
        mockUser.setActive(false);
        mockUser.setLockReason("Tạm khóa do vi phạm");

        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("password");

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tài khoản của bạn đã bị khóa");
    }

    @Test
    @DisplayName("Should throw BadRequestException when role mismatch occurs")
    void authenticate_RoleMismatch_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("password");
        request.setRole("TEACHER");

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");
    }

    @Test
    @DisplayName("Should throw BadRequestException on bad credentials")
    void authenticate_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");
    }

    @Test
    @DisplayName("Should return 2FA response when user is ADMIN")
    void authenticate_AdminUser_Returns2FaRequiredResponse() {
        User adminUser = User.builder()
                .email("admin@test.com")
                .fullName("System Admin")
                .password("encodedPassword")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        adminUser.setId(99L);

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password");

        Authentication authentication = mock(Authentication.class);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userTwoFactorAuthRepository.findByUserId(99L)).thenReturn(Optional.of(UserTwoFactorAuth.builder().isEnabled(true).build()));
        when(jwtUtils.generatePreAuthToken("admin@test.com", 99L, "ADMIN")).thenReturn("pre-auth-token");

        UserInfoResponse response = strategy.authenticate(request, mockResponse);

        assertThat(response).isNotNull();
        assertThat(response.getIs2faRequired()).isTrue();
        assertThat(response.getPreAuthToken()).isEqualTo("pre-auth-token");
        verify(authSessionService, never()).issueAuthSession(any(User.class), anyBoolean(), any(HttpServletResponse.class));
    }
}
