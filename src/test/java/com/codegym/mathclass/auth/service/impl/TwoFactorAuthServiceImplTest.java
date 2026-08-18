package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.request.TwoFactorConfirmRequest;
import com.codegym.mathclass.auth.dto.request.TwoFactorVerifyRequest;
import com.codegym.mathclass.auth.dto.response.TwoFactorConfirmResponse;
import com.codegym.mathclass.auth.dto.response.TwoFactorSetupResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.entity.UserBackupCode;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserBackupCodeRepository;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.service.TotpService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.security.services.CustomUserDetailsService;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
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
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceImplTest {

    @Mock
    private TotpService totpService;

    @Mock
    private UserTwoFactorAuthRepository userTwoFactorAuthRepository;

    @Mock
    private UserBackupCodeRepository userBackupCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private TwoFactorAuthServiceImpl twoFactorAuthService;

    private User adminUser;
    private CustomUserDetails adminUserDetails;
    private HttpServletResponse mockResponse;
    private final String validAuthHeader = "Bearer valid-pre-auth-token";

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .email("admin@test.com")
                .fullName("Admin User")
                .password("encodedPassword")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        adminUser.setId(10L);

        adminUserDetails = new CustomUserDetails(
                10L,
                "Admin User",
                "admin@test.com",
                "encodedPassword",
                true,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        mockResponse = mock(HttpServletResponse.class);
    }

    @Nested
    @DisplayName("initiateSetup Tests")
    class InitiateSetupTests {

        @Test
        @DisplayName("Should generate temp secret and return QR code Data URL when pre-auth token is valid")
        void initiateSetup_ValidPreAuthToken_Success() {
            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(true);
            when(jwtUtils.getUserIdFromPreAuthToken("valid-pre-auth-token")).thenReturn(10L);
            when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
            when(userTwoFactorAuthRepository.findByUserId(10L)).thenReturn(Optional.empty());
            when(totpService.generateSecretKey()).thenReturn("JBSWY3DPEHPK3PXP");
            when(totpService.generateQrCodeDataUrl("admin@test.com", "JBSWY3DPEHPK3PXP"))
                    .thenReturn("data:image/png;base64,mockQrCode");

            TwoFactorSetupResponse response = twoFactorAuthService.initiateSetup(validAuthHeader);

            assertThat(response).isNotNull();
            assertThat(response.getSecretKey()).isEqualTo("JBSWY3DPEHPK3PXP");
            assertThat(response.getQrCodeDataUrl()).isEqualTo("data:image/png;base64,mockQrCode");
            assertThat(response.getManualEntryKey()).isEqualTo("JBSW Y3DP EHPK 3PXP");
            verify(userTwoFactorAuthRepository).save(any(UserTwoFactorAuth.class));
        }

        @Test
        @DisplayName("Should throw BadRequestException when token is invalid or expired")
        void initiateSetup_InvalidToken_ThrowsBadRequestException() {
            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(false);

            assertThatThrownBy(() -> twoFactorAuthService.initiateSetup(validAuthHeader))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Phiên xác thực không hợp lệ hoặc đã hết hạn");
        }
    }

    @Nested
    @DisplayName("confirmSetup Tests")
    class ConfirmSetupTests {

        @Test
        @DisplayName("Should activate 2FA, generate backup codes, and return session on valid OTP code")
        void confirmSetup_ValidOtpCode_Success() {
            TwoFactorConfirmRequest request = new TwoFactorConfirmRequest("123456");
            UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder()
                    .userId(10L)
                    .tempSecretKey("JBSWY3DPEHPK3PXP")
                    .isEnabled(false)
                    .build();

            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(true);
            when(jwtUtils.getUserIdFromPreAuthToken("valid-pre-auth-token")).thenReturn(10L);
            when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
            when(userTwoFactorAuthRepository.findByUserId(10L)).thenReturn(Optional.of(auth2fa));
            when(totpService.verifyCode("JBSWY3DPEHPK3PXP", 123456)).thenReturn(true);
            when(totpService.generateBackupCodes(8)).thenReturn(List.of("CODE-0001", "CODE-0002"));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-backup-code");
            when(customUserDetailsService.loadUserByUsername("admin@test.com")).thenReturn(adminUserDetails);
            when(jwtUtils.generateJwtCookie(any(CustomUserDetails.class), anyBoolean()))
                    .thenReturn(ResponseCookie.from("mathclass_jwt", "jwt").build());
            when(refreshTokenService.createRefreshToken(10L))
                    .thenReturn(RefreshToken.builder().id(1L).token("refresh-token").build());
            when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean()))
                    .thenReturn(ResponseCookie.from("mathclass_refresh", "refresh-token").build());
            when(jwtUtils.generateJwtToken("admin@test.com", "ADMIN")).thenReturn("jwt-token");
            when(userMapper.toUserInfoResponse(eq(adminUserDetails), anyString()))
                    .thenReturn(new UserInfoResponse(10L, "admin@test.com", "Admin User", "ADMIN", null, List.of()));

            TwoFactorConfirmResponse response = twoFactorAuthService.confirmSetup(request, validAuthHeader, mockResponse);

            assertThat(response).isNotNull();
            assertThat(response.getBackupCodes()).containsExactly("CODE-0001", "CODE-0002");
            assertThat(auth2fa.isEnabled()).isTrue();
            assertThat(auth2fa.getSecretKey()).isEqualTo("JBSWY3DPEHPK3PXP");
            assertThat(auth2fa.getTempSecretKey()).isNull();
            verify(userBackupCodeRepository).saveAll(any());
            verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
        }

        @Test
        @DisplayName("Should throw BadRequestException on invalid OTP code")
        void confirmSetup_InvalidOtpCode_ThrowsBadRequestException() {
            TwoFactorConfirmRequest request = new TwoFactorConfirmRequest("999999");
            UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder()
                    .userId(10L)
                    .tempSecretKey("JBSWY3DPEHPK3PXP")
                    .isEnabled(false)
                    .build();

            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(true);
            when(jwtUtils.getUserIdFromPreAuthToken("valid-pre-auth-token")).thenReturn(10L);
            when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
            when(userTwoFactorAuthRepository.findByUserId(10L)).thenReturn(Optional.of(auth2fa));
            when(totpService.verifyCode("JBSWY3DPEHPK3PXP", 999999)).thenReturn(false);

            assertThatThrownBy(() -> twoFactorAuthService.confirmSetup(request, validAuthHeader, mockResponse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã xác thực 6 số không chính xác");
        }
    }

    @Nested
    @DisplayName("verifyLogin Tests")
    class VerifyLoginTests {

        @Test
        @DisplayName("Should login successfully with valid 6-digit TOTP code")
        void verifyLogin_ValidTotpCode_Success() {
            TwoFactorVerifyRequest request = TwoFactorVerifyRequest.builder()
                    .code("654321")
                    .isBackupCode(false)
                    .rememberMe(true)
                    .build();

            UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder()
                    .userId(10L)
                    .secretKey("JBSWY3DPEHPK3PXP")
                    .isEnabled(true)
                    .failedAttempts(2)
                    .build();

            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(true);
            when(jwtUtils.getUserIdFromPreAuthToken("valid-pre-auth-token")).thenReturn(10L);
            when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
            when(userTwoFactorAuthRepository.findByUserId(10L)).thenReturn(Optional.of(auth2fa));
            when(totpService.verifyCode("JBSWY3DPEHPK3PXP", 654321)).thenReturn(true);
            when(customUserDetailsService.loadUserByUsername("admin@test.com")).thenReturn(adminUserDetails);
            when(jwtUtils.generateJwtCookie(any(CustomUserDetails.class), anyBoolean()))
                    .thenReturn(ResponseCookie.from("mathclass_jwt", "jwt").build());
            when(refreshTokenService.createRefreshToken(10L))
                    .thenReturn(RefreshToken.builder().id(1L).token("refresh-token").build());
            when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean()))
                    .thenReturn(ResponseCookie.from("mathclass_refresh", "refresh-token").build());
            when(jwtUtils.generateJwtToken("admin@test.com", "ADMIN")).thenReturn("jwt-token");
            when(userMapper.toUserInfoResponse(eq(adminUserDetails), anyString()))
                    .thenReturn(new UserInfoResponse(10L, "admin@test.com", "Admin User", "ADMIN", null, List.of()));

            UserInfoResponse response = twoFactorAuthService.verifyLogin(request, validAuthHeader, mockResponse);

            assertThat(response).isNotNull();
            assertThat(auth2fa.getFailedAttempts()).isEqualTo(0);
            verify(mockResponse, times(2)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
        }

        @Test
        @DisplayName("Should login successfully with valid unused Backup Code")
        void verifyLogin_ValidBackupCode_Success() {
            TwoFactorVerifyRequest request = TwoFactorVerifyRequest.builder()
                    .code("CODE-1234")
                    .isBackupCode(true)
                    .build();

            UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder()
                    .userId(10L)
                    .secretKey("JBSWY3DPEHPK3PXP")
                    .isEnabled(true)
                    .build();

            UserBackupCode backupCodeEntity = UserBackupCode.builder()
                    .userId(10L)
                    .codeHash("hashed-code-1234")
                    .isUsed(false)
                    .build();

            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(true);
            when(jwtUtils.getUserIdFromPreAuthToken("valid-pre-auth-token")).thenReturn(10L);
            when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
            when(userTwoFactorAuthRepository.findByUserId(10L)).thenReturn(Optional.of(auth2fa));
            when(userBackupCodeRepository.findByUserIdAndIsUsedFalse(10L)).thenReturn(List.of(backupCodeEntity));
            when(passwordEncoder.matches("CODE-1234", "hashed-code-1234")).thenReturn(true);
            when(customUserDetailsService.loadUserByUsername("admin@test.com")).thenReturn(adminUserDetails);
            when(jwtUtils.generateJwtCookie(any(CustomUserDetails.class), anyBoolean()))
                    .thenReturn(ResponseCookie.from("mathclass_jwt", "jwt").build());
            when(refreshTokenService.createRefreshToken(10L))
                    .thenReturn(RefreshToken.builder().id(1L).token("refresh-token").build());
            when(jwtUtils.generateRefreshJwtCookie(anyString(), anyBoolean()))
                    .thenReturn(ResponseCookie.from("mathclass_refresh", "refresh-token").build());
            when(jwtUtils.generateJwtToken("admin@test.com", "ADMIN")).thenReturn("jwt-token");
            when(userMapper.toUserInfoResponse(eq(adminUserDetails), anyString()))
                    .thenReturn(new UserInfoResponse(10L, "admin@test.com", "Admin User", "ADMIN", null, List.of()));

            UserInfoResponse response = twoFactorAuthService.verifyLogin(request, validAuthHeader, mockResponse);

            assertThat(response).isNotNull();
            assertThat(backupCodeEntity.isUsed()).isTrue();
            assertThat(backupCodeEntity.getUsedAt()).isNotNull();
            verify(userBackupCodeRepository).save(backupCodeEntity);
        }

        @Test
        @DisplayName("Should lock account and throw TooManyRequestsException after 5 consecutive failed attempts")
        void verifyLogin_ExceedMaxFailedAttempts_LocksAccount() {
            TwoFactorVerifyRequest request = TwoFactorVerifyRequest.builder()
                    .code("000000")
                    .isBackupCode(false)
                    .build();

            UserTwoFactorAuth auth2fa = UserTwoFactorAuth.builder()
                    .userId(10L)
                    .secretKey("JBSWY3DPEHPK3PXP")
                    .isEnabled(true)
                    .failedAttempts(4)
                    .build();

            when(jwtUtils.validatePreAuthToken("valid-pre-auth-token")).thenReturn(true);
            when(jwtUtils.getUserIdFromPreAuthToken("valid-pre-auth-token")).thenReturn(10L);
            when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
            when(userTwoFactorAuthRepository.findByUserId(10L)).thenReturn(Optional.of(auth2fa));
            when(totpService.verifyCode("JBSWY3DPEHPK3PXP", 0)).thenReturn(false);

            assertThatThrownBy(() -> twoFactorAuthService.verifyLogin(request, validAuthHeader, mockResponse))
                    .isInstanceOf(BadRequestException.class);

            assertThat(auth2fa.getFailedAttempts()).isEqualTo(5);
            assertThat(auth2fa.getLockedUntil()).isNotNull();

            // Next attempt should throw TooManyRequestsException
            assertThatThrownBy(() -> twoFactorAuthService.verifyLogin(request, validAuthHeader, mockResponse))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("quá 5 lần liên tiếp");
        }
    }
}
