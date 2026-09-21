package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
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
import org.springframework.security.authentication.BadCredentialsException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserTwoFactorAuthRepository userTwoFactorAuthRepository;

    @InjectMocks
    private LocalPasswordAuthStrategy strategy;

    private User mockUser;
    private CustomUserDetails mockUserDetails;
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

        mockUserDetails = new CustomUserDetails(
                1L, "Test Student", "student@test.com", "encodedPassword", true, null, Collections.emptyList()
        );

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
    @DisplayName("Should authenticate user and set cookies when credentials are valid")
    void authenticate_ValidCredentials_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("password");

        Authentication authentication = mock(Authentication.class);
        RefreshToken mockRefreshToken = RefreshToken.builder().id(1L).token("refresh-uuid").user(mockUser).expiryDate(Instant.now().plusSeconds(3600)).build();
        UserInfoResponse expectedUserInfo = new UserInfoResponse(1L, "student@test.com", "Test Student", "STUDENT", null, List.of());

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUserDetails);
        when(jwtUtils.generateJwtCookie(eq(mockUserDetails), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_jwt", "jwt-token").build());
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("jwt-token");
        when(refreshTokenService.createRefreshToken(1L)).thenReturn(mockRefreshToken);
        when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_refresh", "refresh-uuid").build());
        when(userMapper.toUserInfoResponse(eq(mockUserDetails), anyString())).thenReturn(expectedUserInfo);

        UserInfoResponse response = strategy.authenticate(request, mockResponse);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("student@test.com");
        verify(mockResponse, times(3)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
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
    @DisplayName("Should throw BadRequestException when user account is locked")
    void authenticate_LockedUser_ThrowsException() {
        mockUser.setActive(false);
        mockUser.setLockReason("Vi phạm tiêu chuẩn.");

        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("password");

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tài khoản của bạn đã bị khóa");
    }

    @Test
    @DisplayName("Should throw BadRequestException when password is incorrect")
    void authenticate_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@test.com");
        request.setPassword("wrong");

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(mockUser));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> strategy.authenticate(request, mockResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");
    }
}
