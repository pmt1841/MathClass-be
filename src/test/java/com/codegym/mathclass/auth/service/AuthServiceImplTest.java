package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.JwtResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private LoginRequest loginRequest;
    private SignupRequest signupRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@gmail.com");
        loginRequest.setPassword("123456");

        signupRequest = new SignupRequest();
        signupRequest.setEmail("newuser@gmail.com");
        signupRequest.setPassword("123456");
        signupRequest.setFullName("New User");
        signupRequest.setRole(Role.STUDENT);
        signupRequest.setPhoneNumber("0123456789");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("newuser@gmail.com");
        testUser.setPassword("encoded_password");
        testUser.setRole(Role.STUDENT);
        testUser.setActive(false);
        testUser.setVerificationCode("dummy-token");
    }

    @Test
    @DisplayName("Should authenticate user and return JWT successfully")
    void authenticateUser_Success() {
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(
                1L, "Test User", "test@gmail.com", "encoded_password", true, Collections.emptyList());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("mock-jwt-token");
        when(authentication.getPrincipal()).thenReturn(userDetails);

        ResponseEntity<?> responseEntity = authService.authenticateUser(loginRequest);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertTrue(responseEntity.getBody() instanceof JwtResponse);

        JwtResponse response = (JwtResponse) responseEntity.getBody();
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("test@gmail.com", response.getEmail());

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils, times(1)).generateJwtToken(authentication);
    }

    @Test
    @DisplayName("Should register user successfully")
    void registerUser_Success() {
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(signupRequest.getPassword())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        ResponseEntity<?> responseEntity = authService.registerUser(signupRequest);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        MessageResponse response = (MessageResponse) responseEntity.getBody();
        assertNotNull(response);
        assertTrue(response.getMessage().contains("Đăng ký tài khoản thành công"));

        verify(userRepository, times(1)).save(any(User.class));
        verify(emailService, times(1)).sendHtmlMailAsync(eq("newuser@gmail.com"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw BadRequestException if email already exists when registering")
    void registerUser_EmailExists() {
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.registerUser(signupRequest));
        assertEquals("Lỗi: Email đã tồn tại!", exception.getMessage());

        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should logout user successfully")
    void logoutUser_Success() {
        ResponseEntity<?> responseEntity = authService.logoutUser();

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        MessageResponse response = (MessageResponse) responseEntity.getBody();
        assertNotNull(response);
        assertEquals("Đăng xuất thành công!", response.getMessage());
    }

    @Test
    @DisplayName("Should verify user successfully with valid token")
    void verifyUser_Success() {
        String token = "dummy-token";
        when(userRepository.findByVerificationCode(token)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        ResponseEntity<?> responseEntity = authService.verifyUser(token);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        MessageResponse response = (MessageResponse) responseEntity.getBody();
        assertNotNull(response);
        assertEquals("Tài khoản đã được kích hoạt thành công!", response.getMessage());

        assertTrue(testUser.isActive());
        assertNull(testUser.getVerificationCode());

        verify(userRepository, times(1)).save(testUser);
        verify(emailService, times(1)).sendHtmlMailAsync(eq(testUser.getEmail()), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw BadRequestException if verification token is invalid")
    void verifyUser_InvalidToken() {
        String token = "invalid-token";
        when(userRepository.findByVerificationCode(token)).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.verifyUser(token));
        assertEquals("Lỗi: Mã xác nhận không hợp lệ!", exception.getMessage());

        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendHtmlMailAsync(anyString(), anyString(), anyString(), any());
    }
}
