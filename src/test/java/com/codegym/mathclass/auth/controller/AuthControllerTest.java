package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Nested
    @DisplayName("POST /auth/login Integration Tests")
    class LoginEndpointTests {

        @Test
        @DisplayName("Should return 200 OK and UserInfoResponse when request is valid")
        void login_ValidRequest_Returns200AndUserInfo() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("test@test.com");
            loginRequest.setPassword("password123");
            loginRequest.setRole("STUDENT");

            UserInfoResponse mockUserInfoResponse = new UserInfoResponse(
                    1L, "test@test.com", "Test User", "STUDENT", null, List.of()
            );

            when(authService.authenticateUser(any(LoginRequest.class), any())).thenReturn(mockUserInfoResponse);

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("test@test.com"))
                    .andExpect(jsonPath("$.userRole").value("STUDENT"));

            verify(authService, times(1)).authenticateUser(any(LoginRequest.class), any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when email is blank")
        void login_BlankEmail_Returns400BadRequest() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("");
            loginRequest.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateUser(any(), any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when email format is invalid")
        void login_InvalidEmailFormat_Returns400BadRequest() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("invalid-email-format");
            loginRequest.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateUser(any(), any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when password is shorter than 6 chars")
        void login_ShortPassword_Returns400BadRequest() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("test@test.com");
            loginRequest.setPassword("123"); // < 6 chars

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateUser(any(), any());
        }
    }

    @Nested
    @DisplayName("POST /auth/google Integration Tests")
    class GoogleAuthEndpointTests {

        @Test
        @DisplayName("Should return 200 OK when credential is valid")
        void googleAuth_ValidRequest_Returns200AndUserInfo() throws Exception {
            GoogleAuthRequest googleRequest = new GoogleAuthRequest();
            googleRequest.setCredential("mockGoogleToken");
            googleRequest.setRole("STUDENT");

            UserInfoResponse mockUserInfoResponse = new UserInfoResponse(
                    1L, "google@test.com", "Google User", "STUDENT", "http://avatar.url", List.of()
            );

            when(authService.authenticateWithGoogle(any(GoogleAuthRequest.class), any())).thenReturn(mockUserInfoResponse);

            mockMvc.perform(post("/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(googleRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("google@test.com"));

            verify(authService, times(1)).authenticateWithGoogle(any(GoogleAuthRequest.class), any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when credential is blank")
        void googleAuth_BlankCredential_Returns400BadRequest() throws Exception {
            GoogleAuthRequest googleRequest = new GoogleAuthRequest();
            googleRequest.setCredential("");

            mockMvc.perform(post("/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(googleRequest)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateWithGoogle(any(), any());
        }
    }

    @Nested
    @DisplayName("POST /auth/register Integration Tests")
    class RegisterEndpointTests {

        @Test
        @DisplayName("Should return 200 OK when SignupRequest is valid")
        void register_ValidRequest_Returns200AndMessage() throws Exception {
            SignupRequest signupRequest = new SignupRequest();
            signupRequest.setEmail("newuser@test.com");
            signupRequest.setPassword("Password123");
            signupRequest.setFullName("New User");
            signupRequest.setPhoneNumber("0987654321");
            signupRequest.setRole(Role.STUDENT);

            MessageResponse mockMessageResponse = new MessageResponse("Đăng ký tài khoản thành công!");
            when(authService.registerUser(any(SignupRequest.class))).thenReturn(mockMessageResponse);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đăng ký tài khoản thành công!"));

            verify(authService, times(1)).registerUser(any(SignupRequest.class));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when fullName is missing")
        void register_MissingFullName_Returns400BadRequest() throws Exception {
            SignupRequest signupRequest = new SignupRequest();
            signupRequest.setEmail("newuser@test.com");
            signupRequest.setPassword("Password123");
            signupRequest.setFullName(""); // Blank
            signupRequest.setPhoneNumber("0987654321");
            signupRequest.setRole(Role.STUDENT);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).registerUser(any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when role is null")
        void register_NullRole_Returns400BadRequest() throws Exception {
            SignupRequest signupRequest = new SignupRequest();
            signupRequest.setEmail("newuser@test.com");
            signupRequest.setPassword("Password123");
            signupRequest.setFullName("New User");
            signupRequest.setPhoneNumber("0987654321");
            signupRequest.setRole(null); // Null role

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).registerUser(any());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout & /refreshtoken Integration Tests")
    class SessionEndpointTests {

        @Test
        @DisplayName("POST /auth/logout should return 200 OK")
        void logout_Returns200AndMessage() throws Exception {
            MessageResponse mockMessageResponse = new MessageResponse("Đăng xuất thành công!");
            when(authService.logoutUser(any(), any())).thenReturn(mockMessageResponse);

            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đăng xuất thành công!"));

            verify(authService, times(1)).logoutUser(any(), any());
        }

        @Test
        @DisplayName("POST /auth/refresh-token should return 200 OK")
        void refreshtoken_Returns200AndMessage() throws Exception {
            MessageResponse mockMessageResponse = new MessageResponse("Token is refreshed successfully!");
            when(authService.refreshToken(any(), any())).thenReturn(mockMessageResponse);

            mockMvc.perform(post("/auth/refresh-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Token is refreshed successfully!"));

            verify(authService, times(1)).refreshToken(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /auth/verify Integration Tests")
    class VerifyEndpointTests {

        @Test
        @DisplayName("Should return 200 OK when token query param is provided")
        void verifyUser_ValidToken_Returns200AndMessage() throws Exception {
            String token = "valid-token-uuid";
            MessageResponse mockMessageResponse = new MessageResponse("Tài khoản đã được kích hoạt thành công!");
            when(authService.verifyUser(token)).thenReturn(mockMessageResponse);

            mockMvc.perform(get("/auth/verify")
                    .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Tài khoản đã được kích hoạt thành công!"));

            verify(authService, times(1)).verifyUser(token);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when token query param is missing")
        void verifyUser_MissingTokenParam_Returns400BadRequest() throws Exception {
            mockMvc.perform(get("/auth/verify"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).verifyUser(any());
        }
    }

    @Nested
    @DisplayName("POST /auth/forgot-password Integration Tests")
    class ForgotPasswordEndpointTests {

        @Test
        @DisplayName("Should return 200 OK when email is valid")
        void forgotPassword_ValidRequest_Returns200AndMessage() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("user@example.com");

            MessageResponse mockResponse = new MessageResponse(
                    "Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư.");
            when(authService.forgotPassword(any(ForgotPasswordRequest.class))).thenReturn(mockResponse);

            mockMvc.perform(post("/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư."));

            verify(authService, times(1)).forgotPassword(any(ForgotPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when email is invalid format")
        void forgotPassword_InvalidEmail_Returns400BadRequest() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("not-an-email");

            mockMvc.perform(post("/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).forgotPassword(any());
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password Integration Tests")
    class ResetPasswordEndpointTests {

        @Test
        @DisplayName("Should return 200 OK when token and strong password are provided")
        void resetPassword_ValidRequest_Returns200AndMessage() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("valid-token-hash");
            request.setNewPassword("StrongPass123!");

            MessageResponse mockResponse = new MessageResponse(
                    "Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
            when(authService.resetPassword(any(ResetPasswordRequest.class))).thenReturn(mockResponse);

            mockMvc.perform(post("/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới."));

            verify(authService, times(1)).resetPassword(any(ResetPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when newPassword is weak (missing uppercase/digit or < 8 chars)")
        void resetPassword_WeakPassword_Returns400BadRequest() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("valid-token-hash");
            request.setNewPassword("123456"); // Weak password

            mockMvc.perform(post("/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).resetPassword(any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when token is blank")
        void resetPassword_BlankToken_Returns400BadRequest() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken(""); // Blank token
            request.setNewPassword("StrongPass123!");

            mockMvc.perform(post("/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).resetPassword(any());
        }
    }
}
