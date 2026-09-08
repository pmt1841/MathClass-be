package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.dto.request.AuthType;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.service.TotpService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPortalAuthStrategyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserTwoFactorAuthRepository userTwoFactorAuthRepository;

    @Mock
    private TotpService totpService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminPortalAuthStrategy strategy;

    private User adminUser;
    private CustomUserDetails adminDetails;
    private HttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .email("admin@test.com")
                .fullName("Admin User")
                .password("encodedPassword")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        adminUser.setId(99L);

        adminDetails = new CustomUserDetails(
                99L, "Admin User", "admin@test.com", "encodedPassword", true, null, Collections.emptyList()
        );

        mockResponse = mock(HttpServletResponse.class);
    }

    @Test
    @DisplayName("Should return true when supports AuthType.ADMIN_2FA")
    void supports_Admin2Fa_ReturnsTrue() {
        assertThat(strategy.supports(AuthType.ADMIN_2FA)).isTrue();
        assertThat(strategy.supports(AuthType.LOCAL)).isFalse();
    }

    @Test
    @DisplayName("Should authenticate admin user when credentials and OTP code are valid")
    void authenticate_ValidCredentialsAndOtp_Success() {
        Admin2FaLoginRequest request = new Admin2FaLoginRequest("admin@test.com", "password", "123456");
        Authentication authentication = mock(Authentication.class);
        UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder().userId(99L).isEnabled(true).secretKey("SECRET").build();
        RefreshToken mockRefreshToken = RefreshToken.builder().id(1L).token("refresh-uuid").user(adminUser).expiryDate(Instant.now().plusSeconds(3600)).build();
        UserInfoResponse expectedUserInfo = new UserInfoResponse(99L, "admin@test.com", "Admin User", "ADMIN", null, List.of());

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userTwoFactorAuthRepository.findByUserId(99L)).thenReturn(Optional.of(auth2fa));
        when(totpService.verifyCode(eq("SECRET"), eq(123456))).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(adminDetails);
        when(jwtUtils.generateJwtCookie(eq(adminDetails), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_jwt", "jwt").build());
        when(refreshTokenService.createRefreshToken(99L)).thenReturn(mockRefreshToken);
        when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_refresh", "refresh").build());
        when(userMapper.toUserInfoResponse(eq(adminDetails), any())).thenReturn(expectedUserInfo);

        UserInfoResponse response = strategy.authenticate(request, mockResponse);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("admin@test.com");
        verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user is not ADMIN role")
    void authenticate_NonAdminUser_ThrowsAccessDeniedException() {
        User studentUser = User.builder().email("student@test.com").role(Role.STUDENT).isActive(true).build();
        studentUser.setId(1L);

        Admin2FaLoginRequest request = new Admin2FaLoginRequest("student@test.com", "password", "123456");

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(studentUser));

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tài khoản không có quyền truy cập hệ thống Quản trị viên.");
    }

    @Test
    @DisplayName("Should throw BadRequestException when OTP code is incorrect")
    void authenticate_InvalidOtp_ThrowsException() {
        Admin2FaLoginRequest request = new Admin2FaLoginRequest("admin@test.com", "password", "000000");
        Authentication authentication = mock(Authentication.class);
        UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder().userId(99L).isEnabled(true).secretKey("SECRET").build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userTwoFactorAuthRepository.findByUserId(99L)).thenReturn(Optional.of(auth2fa));
        when(totpService.verifyCode(eq("SECRET"), eq(0))).thenReturn(false);

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mã xác thực 2FA không chính xác");
    }
}
