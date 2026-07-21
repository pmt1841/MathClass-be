package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.JwtResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .build();
    }

    // ==========================================
    // Tests for login
    // ==========================================

    @Test
    @DisplayName("Should login and return JWT Response")
    void login_ValidRequest_ReturnsOkAndJwtResponse() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("password");

        JwtResponse mockJwtResponse = new JwtResponse("mockJwt", 1L, "test@test.com", "Test User", "STUDENT", null, null);
        when(authService.authenticateUser(any(LoginRequest.class))).thenReturn(mockJwtResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mockJwt"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.userRole").value("STUDENT"));
                
        verify(authService, times(1)).authenticateUser(any(LoginRequest.class));
    }

    // ==========================================
    // Tests for googleAuth
    // ==========================================

    @Test
    @DisplayName("Should authenticate with Google and return JWT Response")
    void googleAuth_ValidRequest_ReturnsOkAndJwtResponse() throws Exception {
        // Given
        GoogleAuthRequest googleRequest = new GoogleAuthRequest();
        googleRequest.setCredential("mockGoogleToken");
        
        JwtResponse mockJwtResponse = new JwtResponse("mockJwt", 1L, "google@test.com", "Google User", "STUDENT", "url", null);
        when(authService.authenticateWithGoogle(any(GoogleAuthRequest.class))).thenReturn(mockJwtResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mockJwt"))
                .andExpect(jsonPath("$.email").value("google@test.com"));
                
        verify(authService, times(1)).authenticateWithGoogle(any(GoogleAuthRequest.class));
    }

    // ==========================================
    // Tests for register
    // ==========================================

    @Test
    @DisplayName("Should register new user and return success message")
    void register_ValidRequest_ReturnsOkAndMessage() throws Exception {
        // Given
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setEmail("new@test.com");
        signupRequest.setPassword("password");
        signupRequest.setFullName("New User");
        signupRequest.setPhoneNumber("0123456789");
        signupRequest.setRole(com.codegym.mathclass.user.entity.Role.STUDENT);

        MessageResponse mockMessageResponse = new MessageResponse("Đăng ký tài khoản thành công!");
        when(authService.registerUser(any(SignupRequest.class))).thenReturn(mockMessageResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng ký tài khoản thành công!"));
                
        verify(authService, times(1)).registerUser(any(SignupRequest.class));
    }

    // ==========================================
    // Tests for logout
    // ==========================================

    @Test
    @DisplayName("Should logout user and return success message")
    void logout_Always_ReturnsOkAndMessage() throws Exception {
        // Given
        MessageResponse mockMessageResponse = new MessageResponse("Đăng xuất thành công!");
        when(authService.logoutUser()).thenReturn(mockMessageResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công!"));
                
        verify(authService, times(1)).logoutUser();
    }

    // ==========================================
    // Tests for verifyUser
    // ==========================================

    @Test
    @DisplayName("Should verify user token and return success message")
    void verifyUser_ValidToken_ReturnsOkAndMessage() throws Exception {
        // Given
        String token = "valid-token";
        MessageResponse mockMessageResponse = new MessageResponse("Tài khoản đã được kích hoạt thành công!");
        when(authService.verifyUser(token)).thenReturn(mockMessageResponse);

        // When & Then
        mockMvc.perform(get("/api/auth/verify")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tài khoản đã được kích hoạt thành công!"));
                
        verify(authService, times(1)).verifyUser(token);
    }

    // ==========================================
    // Tests for forgotPassword
    // ==========================================

    @Test
    @DisplayName("Should process forgot password and return message response")
    void forgotPassword_ValidRequest_ReturnsOkAndMessage() throws Exception {
        // Given
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");

        MessageResponse mockResponse = new MessageResponse("Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư.");
        when(authService.forgotPassword(any(ForgotPasswordRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư."));

        verify(authService, times(1)).forgotPassword(any(ForgotPasswordRequest.class));
    }

    // ==========================================
    // Tests for resetPassword
    // ==========================================

    @Test
    @DisplayName("Should reset password and return success message")
    void resetPassword_ValidRequest_ReturnsOkAndMessage() throws Exception {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("SecurePassword123!");

        MessageResponse mockResponse = new MessageResponse("Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
        when(authService.resetPassword(any(ResetPasswordRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới."));

        verify(authService, times(1)).resetPassword(any(ResetPasswordRequest.class));
    }
}
