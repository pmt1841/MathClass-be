package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceImplTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthSessionServiceImpl authSessionService;

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
    @DisplayName("issueAuthSession should set access/refresh/logout cookies, update last active, and return UserInfoResponse")
    void issueAuthSession_Success() {
        RefreshToken refreshToken = RefreshToken.builder()
                .id(10L)
                .token("refresh-token-val")
                .user(mockUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();
        UserInfoResponse expectedResponse = new UserInfoResponse(1L, "student@test.com", "Test Student", "STUDENT", null, List.of());

        when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(Collections.emptyList());
        when(jwtUtils.generateJwtCookie(any(CustomUserDetails.class), eq(true)))
                .thenReturn(ResponseCookie.from("mathclass_jwt", "jwt-cookie-val").build());
        when(refreshTokenService.createRefreshToken(1L)).thenReturn(refreshToken);
        when(jwtUtils.generateRefreshJwtCookie(eq("refresh-token-val"), eq(true)))
                .thenReturn(ResponseCookie.from("mathclass_refresh", "refresh-cookie-val").build());
        when(jwtUtils.generateJwtToken(eq("student@test.com"), eq("STUDENT"))).thenReturn("signed-jwt");
        when(userMapper.toUserInfoResponse(any(CustomUserDetails.class), eq("signed-jwt"))).thenReturn(expectedResponse);

        UserInfoResponse actual = authSessionService.issueAuthSession(mockUser, true, mockResponse);

        assertThat(actual).isNotNull();
        assertThat(actual.getEmail()).isEqualTo("student@test.com");
        verify(userRepository).save(mockUser);
        assertThat(mockUser.getLastActiveAt()).isNotNull();
        verify(mockResponse, times(3)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    @DisplayName("clearSessionCookies should add clean access and clean refresh cookie headers")
    void clearSessionCookies_Success() {
        when(jwtUtils.getCleanJwtCookie()).thenReturn(ResponseCookie.from("mathclass_jwt", "").maxAge(0).build());
        when(jwtUtils.getCleanJwtRefreshCookie()).thenReturn(ResponseCookie.from("mathclass_refresh", "").maxAge(0).build());

        authSessionService.clearSessionCookies(mockResponse);

        verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    @DisplayName("setRefreshAccessCookie should add refreshed access cookie header")
    void setRefreshAccessCookie_Success() {
        when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(Collections.emptyList());
        when(jwtUtils.generateJwtCookie(any(CustomUserDetails.class)))
                .thenReturn(ResponseCookie.from("mathclass_jwt", "new-jwt-cookie").build());

        authSessionService.setRefreshAccessCookie(mockUser, mockResponse);

        verify(mockResponse, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }
}
