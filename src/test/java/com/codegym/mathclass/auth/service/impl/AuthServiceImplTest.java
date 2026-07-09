package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.JwtResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.thymeleaf.context.Context;

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
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@test.com");
        mockUser.setFullName("Test User");
        mockUser.setRole(Role.STUDENT);
        mockUser.setPassword("encodedPassword");

        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"));
        mockUserDetails = new CustomUserDetails(1L, "Test User", "test@test.com", "password", true, null, authorities);
    }

    // ==========================================
    // Tests for authenticateUser
    // ==========================================

    @Test
    @DisplayName("Should authenticate user and return JWT response")
    void authenticateUser_ValidCredentials_ReturnsJwtResponse() {
        // Given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("password");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("mockJwtToken");
        when(authentication.getPrincipal()).thenReturn(mockUserDetails);

        // When
        JwtResponse response = authService.authenticateUser(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mockJwtToken");
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getUserRole()).isEqualTo("STUDENT");
        
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils, times(1)).generateJwtToken(authentication);
    }

    // ==========================================
    // Tests for logoutUser
    // ==========================================

    @Test
    @DisplayName("Should logout user and return success message")
    void logoutUser_Always_ReturnsSuccessMessage() {
        // When
        MessageResponse response = authService.logoutUser();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Đăng xuất thành công!");
    }

    // ==========================================
    // Tests for registerUser
    // ==========================================

    @Test
    @DisplayName("Should register user successfully and send verification email")
    void registerUser_EmailDoesNotExist_RegistersUserAndSendsEmail() {
        // Given
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setEmail("new@test.com");
        signupRequest.setPassword("password");
        signupRequest.setFullName("New User");
        signupRequest.setRole(Role.STUDENT);

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(encoder.encode("password")).thenReturn("encodedPassword");

        // When
        MessageResponse response = authService.registerUser(signupRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Đăng ký tài khoản thành công");
        
        verify(userRepository, times(1)).save(any(User.class));
        verify(notificationSettingsRepository, times(1)).save(any(NotificationSettings.class));
        verify(emailService, times(1)).sendHtmlMailAsync(eq("new@test.com"), eq("Xác nhận đăng ký tài khoản MathClass"), eq("auth-verify"), any(Context.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException if email already exists on registration")
    void registerUser_EmailExists_ThrowsBadRequestException() {
        // Given
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setEmail("existing@test.com");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.registerUser(signupRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lỗi: Email đã tồn tại!");
                
        verify(userRepository, never()).save(any(User.class));
    }

    // ==========================================
    // Tests for verifyUser
    // ==========================================

    @Test
    @DisplayName("Should verify user successfully with valid token")
    void verifyUser_ValidToken_VerifiesUserAndSendsWelcomeEmail() {
        // Given
        String token = "valid-token";
        mockUser.setVerificationCode(token);
        
        when(userRepository.findByVerificationCode(token)).thenReturn(Optional.of(mockUser));

        // When
        MessageResponse response = authService.verifyUser(token);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Tài khoản đã được kích hoạt thành công!");
        assertThat(mockUser.isActive()).isTrue();
        assertThat(mockUser.getVerificationCode()).isNull();
        
        verify(userRepository, times(1)).save(mockUser);
        verify(emailService, times(1)).sendHtmlMailAsync(eq("test@test.com"), eq("Kích hoạt tài khoản thành công"), eq("auth-welcome"), any(Context.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException if verification token is invalid")
    void verifyUser_InvalidToken_ThrowsBadRequestException() {
        // Given
        String token = "invalid-token";
        when(userRepository.findByVerificationCode(token)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.verifyUser(token))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lỗi: Mã xác nhận không hợp lệ!");
                
        verify(userRepository, never()).save(any(User.class));
    }
}
