package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.auth.service.UserRegistrationService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
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
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
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
    private PermissionCacheService permissionCacheService;

    @Mock
    private UserRegistrationService userRegistrationService;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private RestTemplate mockRestTemplate;

    private GoogleOAuth2AuthStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GoogleOAuth2AuthStrategy(
                userRepository,
                permissionCacheService,
                userRegistrationService,
                authSessionService,
                mockRestTemplate
        );
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

        UserInfoResponse expectedResponse = new UserInfoResponse();
        when(authSessionService.issueAuthSession(eq(existingUser), anyBoolean(), eq(response))).thenReturn(expectedResponse);

        UserInfoResponse actual = strategy.authenticate(request, response);

        assertNotNull(actual);
        verify(authSessionService).issueAuthSession(existingUser, false, response);
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
        User newUser = User.builder()
                .email("newteacher@gmail.com")
                .fullName("New Teacher")
                .avatarUrl("https://avatar.com/teacher.jpg")
                .role(Role.TEACHER)
                .isActive(true)
                .provider(Provider.GOOGLE)
                .build();
        newUser.setId(99L);

        when(userRegistrationService.registerOAuth2User(
                eq("newteacher@gmail.com"),
                eq("New Teacher"),
                eq("https://avatar.com/teacher.jpg"),
                eq(Role.TEACHER)
        )).thenReturn(newUser);

        when(permissionCacheService.getPermissionsByRole(Role.TEACHER)).thenReturn(Collections.emptyList());

        UserInfoResponse expectedResponse = new UserInfoResponse();
        when(authSessionService.issueAuthSession(eq(newUser), anyBoolean(), eq(response))).thenReturn(expectedResponse);

        UserInfoResponse actual = strategy.authenticate(request, response);

        assertNotNull(actual);
        verify(userRegistrationService, times(1)).registerOAuth2User(
                "newteacher@gmail.com", "New Teacher", "https://avatar.com/teacher.jpg", Role.TEACHER);
        verify(authSessionService).issueAuthSession(eq(newUser), eq(false), eq(response));
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
