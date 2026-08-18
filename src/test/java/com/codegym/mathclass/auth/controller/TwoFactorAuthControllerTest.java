package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.TwoFactorConfirmRequest;
import com.codegym.mathclass.auth.dto.request.TwoFactorVerifyRequest;
import com.codegym.mathclass.auth.dto.response.TwoFactorConfirmResponse;
import com.codegym.mathclass.auth.dto.response.TwoFactorSetupResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.service.TwoFactorAuthService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.GlobalExceptionHandler;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TwoFactorAuthService twoFactorAuthService;

    @InjectMocks
    private TwoFactorAuthController twoFactorAuthController;

    private ObjectMapper objectMapper;
    private final String authHeader = "Bearer valid-pre-auth-token";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(twoFactorAuthController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /auth/2fa/setup Tests")
    class SetupEndpointTests {

        @Test
        @DisplayName("Should return 200 OK with QR Code Data URL and Secret Key")
        void setup_ValidHeader_Returns200AndSetupResponse() throws Exception {
            TwoFactorSetupResponse response = TwoFactorSetupResponse.builder()
                    .secretKey("JBSWY3DPEHPK3PXP")
                    .qrCodeDataUrl("data:image/png;base64,mockQr")
                    .manualEntryKey("JBSW Y3DP EHPK 3PXP")
                    .build();

            when(twoFactorAuthService.initiateSetup(authHeader)).thenReturn(response);

            mockMvc.perform(post("/auth/2fa/setup")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.secretKey").value("JBSWY3DPEHPK3PXP"))
                    .andExpect(jsonPath("$.qrCodeDataUrl").value("data:image/png;base64,mockQr"))
                    .andExpect(jsonPath("$.manualEntryKey").value("JBSW Y3DP EHPK 3PXP"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when preAuthToken is invalid")
        void setup_InvalidHeader_Returns400() throws Exception {
            when(twoFactorAuthService.initiateSetup(authHeader))
                    .thenThrow(new BadRequestException("Phiên xác thực không hợp lệ hoặc đã hết hạn."));

            mockMvc.perform(post("/auth/2fa/setup")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Phiên xác thực không hợp lệ hoặc đã hết hạn."));
        }
    }

    @Nested
    @DisplayName("POST /auth/2fa/setup/confirm Tests")
    class ConfirmEndpointTests {

        @Test
        @DisplayName("Should return 200 OK with backup codes on valid 6-digit code")
        void confirm_ValidCode_Returns200AndBackupCodes() throws Exception {
            TwoFactorConfirmRequest request = new TwoFactorConfirmRequest("123456");
            TwoFactorConfirmResponse response = TwoFactorConfirmResponse.builder()
                    .userInfo(new UserInfoResponse(1L, "admin@test.com", "Admin", "ADMIN", null, List.of()))
                    .backupCodes(List.of("CODE-0001", "CODE-0002"))
                    .message("Kích hoạt xác thực 2 bước thành công!")
                    .build();

            when(twoFactorAuthService.confirmSetup(eq(request), eq(authHeader), any(HttpServletResponse.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/auth/2fa/setup/confirm")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Kích hoạt xác thực 2 bước thành công!"))
                    .andExpect(jsonPath("$.backupCodes[0]").value("CODE-0001"))
                    .andExpect(jsonPath("$.backupCodes[1]").value("CODE-0002"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when code is invalid format (not 6 digits)")
        void confirm_InvalidFormatCode_Returns400() throws Exception {
            TwoFactorConfirmRequest request = new TwoFactorConfirmRequest("abc");

            mockMvc.perform(post("/auth/2fa/setup/confirm")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /auth/2fa/verify Tests")
    class VerifyEndpointTests {

        @Test
        @DisplayName("Should return 200 OK and UserInfo on valid verification")
        void verify_ValidRequest_Returns200AndUserInfo() throws Exception {
            TwoFactorVerifyRequest request = TwoFactorVerifyRequest.builder()
                    .code("654321")
                    .isBackupCode(false)
                    .build();

            UserInfoResponse userInfo = new UserInfoResponse(1L, "admin@test.com", "Admin", "ADMIN", null, List.of());

            when(twoFactorAuthService.verifyLogin(any(TwoFactorVerifyRequest.class), eq(authHeader), any(HttpServletResponse.class)))
                    .thenReturn(userInfo);

            mockMvc.perform(post("/auth/2fa/verify")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("admin@test.com"))
                    .andExpect(jsonPath("$.userRole").value("ADMIN"));
        }

        @Test
        @DisplayName("Should return 429 Too Many Requests when account is locked out")
        void verify_LockedAccount_Returns429() throws Exception {
            TwoFactorVerifyRequest request = TwoFactorVerifyRequest.builder()
                    .code("000000")
                    .isBackupCode(false)
                    .build();

            when(twoFactorAuthService.verifyLogin(any(TwoFactorVerifyRequest.class), eq(authHeader), any(HttpServletResponse.class)))
                    .thenThrow(new TooManyRequestsException("Bạn đã nhập sai mã xác thực quá 5 lần liên tiếp."));

            mockMvc.perform(post("/auth/2fa/verify")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value("Bạn đã nhập sai mã xác thực quá 5 lần liên tiếp."));
        }
    }
}
