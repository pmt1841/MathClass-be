package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2AuthStrategyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private RestTemplate mockRestTemplate;

    private GoogleOAuth2AuthStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GoogleOAuth2AuthStrategy(
                userRepository,
                notificationSettingsRepository,
                permissionCacheService,
                jwtUtils,
                refreshTokenService,
                userMapper,
                aiCreditService
        );
        ReflectionTestUtils.setField(strategy, "restTemplate", mockRestTemplate);
    }

    @Test
    @DisplayName("supports chỉ trả về true với AuthType.GOOGLE")
    void testSupports() {
        assertTrue(strategy.supports(AuthType.GOOGLE));
        assertFalse(strategy.supports(AuthType.LOCAL));
    }

    @Test
    @DisplayName("authenticate thành công khi user đã tồn tại và đang hoạt động")
    void testAuthenticateExistingActiveUser() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCredential("valid-google-token");
        request.setRole("STUDENT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> googleProfile = new HashMap<>();
        googleProfile.put("email", "student@gmail.com");
        googleProfile.put("name", "Student Name");
        googleProfile.put("picture", "https://avatar.com/pic.jpg");

        User existingUser = User.builder()
                .email("student@gmail.com")
                .fullName("Student Name")
                .role(Role.STUDENT)
                .provider(Provider.GOOGLE)
                .isActive(true)
                .build();
        existingUser.setId(10L);

        existingUser.setAvatarUrl("https://avatar.com/pic.jpg");

        when(mockRestTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(googleProfile));

        when(userRepository.findByEmail("student@gmail.com")).thenReturn(Optional.of(existingUser));
        when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(Collections.emptyList());
        when(jwtUtils.generateJwtCookie(any(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_jwt", "jwt").build());
        when(refreshTokenService.createRefreshToken(anyLong())).thenReturn(RefreshToken.builder().token("refresh-token").build());
        when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_refresh", "refresh").build());
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("signed-jwt");

        UserInfoResponse expectedResponse = new UserInfoResponse();
        when(userMapper.toUserInfoResponse(any(), eq("signed-jwt"))).thenReturn(expectedResponse);

        UserInfoResponse actual = strategy.authenticate(request, response);

        assertNotNull(actual);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("authenticate tự động đăng ký mới khi user chưa tồn tại")
    void testAuthenticateNewUserRegistration() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCredential("new-google-token");
        request.setRole("TEACHER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> googleProfile = new HashMap<>();
        googleProfile.put("email", "newteacher@gmail.com");
        googleProfile.put("name", "New Teacher");
        googleProfile.put("picture", "https://avatar.com/teacher.jpg");

        when(mockRestTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(googleProfile));

        when(userRepository.findByEmail("newteacher@gmail.com")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(99L);
            return u;
        }).when(userRepository).save(any(User.class));

        when(permissionCacheService.getPermissionsByRole(Role.TEACHER)).thenReturn(Collections.emptyList());
        when(jwtUtils.generateJwtCookie(any(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_jwt", "jwt").build());
        when(refreshTokenService.createRefreshToken(anyLong())).thenReturn(RefreshToken.builder().token("refresh-token").build());
        when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean())).thenReturn(ResponseCookie.from("mathclass_refresh", "refresh").build());
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("signed-jwt");

        UserInfoResponse expectedResponse = new UserInfoResponse();
        when(userMapper.toUserInfoResponse(any(), eq("signed-jwt"))).thenReturn(expectedResponse);

        UserInfoResponse actual = strategy.authenticate(request, response);

        assertNotNull(actual);
        verify(userRepository).save(any(User.class));
        verify(aiCreditService).grantDefaultForNewUser(eq(99L), eq(Role.TEACHER));
        verify(notificationSettingsRepository).save(any());
    }

    @Test
    @DisplayName("authenticate ném BadRequestException khi tài khoản bị khóa")
    void testAuthenticateLockedAccountThrowsBadRequestException() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCredential("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> googleProfile = Map.of("email", "locked@gmail.com", "name", "Locked");
        User lockedUser = User.builder().email("locked@gmail.com").isActive(false).build();

        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(googleProfile));
        when(userRepository.findByEmail("locked@gmail.com")).thenReturn(Optional.of(lockedUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> strategy.authenticate(request, response));
        assertTrue(ex.getMessage().contains("bị khóa"));
    }

    @Test
    @DisplayName("authenticate ném BadRequestException khi học sinh đăng nhập vào cổng giáo viên")
    void testAuthenticateRoleMismatchThrowsBadRequestException() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCredential("valid-token");
        request.setRole("TEACHER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> googleProfile = Map.of("email", "student@gmail.com", "name", "Student");
        User studentUser = User.builder().email("student@gmail.com").isActive(true).role(Role.STUDENT).build();

        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(googleProfile));
        when(userRepository.findByEmail("student@gmail.com")).thenReturn(Optional.of(studentUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> strategy.authenticate(request, response));
        assertTrue(ex.getMessage().contains("không thể truy cập"));
    }

    @Test
    @DisplayName("authenticate ném BadRequestException khi Google API thất bại")
    void testAuthenticateGoogleApiFailureThrowsBadRequestException() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCredential("bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.badRequest().build());

        assertThrows(BadRequestException.class, () -> strategy.authenticate(request, response));
    }
}
