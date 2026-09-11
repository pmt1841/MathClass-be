package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminAuthController adminAuthController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminAuthController).build();
    }

    @Test
    @DisplayName("POST /auth/admin/login-2fa - Đăng nhập 2FA thành công")
    void login2Fa_Success() throws Exception {
        Admin2FaLoginRequest request = new Admin2FaLoginRequest(
                "admin@mathclass.edu.vn",
                "Password123!",
                "123456"
        );

        UserInfoResponse response = UserInfoResponse.builder()
                .id(1L)
                .email("admin@mathclass.edu.vn")
                .fullName("System Administrator")
                .userRole("ADMIN")
                .permissions(List.of("ALL"))
                .token("jwt-mock-token-admin")
                .build();

        when(authService.authenticateAdmin2Fa(any(Admin2FaLoginRequest.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/auth/admin/login-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("admin@mathclass.edu.vn"))
                .andExpect(jsonPath("$.userRole").value("ADMIN"))
                .andExpect(jsonPath("$.token").value("jwt-mock-token-admin"));
    }

    @Test
    @DisplayName("POST /auth/admin/login-2fa - Validation lỗi khi OTP không đúng 6 ký tự")
    void login2Fa_InvalidOtp_ReturnsBadRequest() throws Exception {
        Admin2FaLoginRequest request = new Admin2FaLoginRequest(
                "admin@mathclass.edu.vn",
                "Password123!",
                "123" // Invalid OTP length
        );

        mockMvc.perform(post("/auth/admin/login-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
