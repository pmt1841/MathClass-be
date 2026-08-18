package com.codegym.mathclass.auth.service.impl;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceImplTest {

    private TotpServiceImpl totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpServiceImpl();
    }

    @Test
    @DisplayName("UT-BE-01: generateSecretKey should return valid Base32 key")
    void generateSecretKey_shouldReturnValidBase32String() {
        String secretKey = totpService.generateSecretKey();

        assertThat(secretKey).isNotNull();
        assertThat(secretKey.length()).isGreaterThanOrEqualTo(16);
        assertThat(secretKey).matches("^[A-Z2-7]+$");
    }

    @Test
    @DisplayName("UT-BE-02: generateQrCodeDataUrl should return valid PNG Data URL")
    void generateQrCodeDataUrl_shouldReturnValidPngBase64() {
        String secretKey = totpService.generateSecretKey();
        String dataUrl = totpService.generateQrCodeDataUrl("admin@test.com", secretKey);

        assertThat(dataUrl).isNotNull();
        assertThat(dataUrl).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("UT-BE-03: verifyCode with valid TOTP code should return true")
    void verifyTotpCode_validCode_shouldReturnTrue() {
        String secretKey = totpService.generateSecretKey();
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        int validCode = gAuth.getTotpPassword(secretKey);

        boolean result = totpService.verifyCode(secretKey, validCode);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("UT-BE-05: verifyCode with invalid code should return false")
    void verifyTotpCode_invalidCode_shouldReturnFalse() {
        String secretKey = totpService.generateSecretKey();

        boolean result = totpService.verifyCode(secretKey, 999999);

        // Extremely unlikely to match valid window
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyCode with null or empty secret should return false")
    void verifyTotpCode_nullOrEmptySecret_shouldReturnFalse() {
        assertThat(totpService.verifyCode(null, 123456)).isFalse();
        assertThat(totpService.verifyCode("", 123456)).isFalse();
    }

    @Test
    @DisplayName("generateBackupCodes should return specified count of formatted codes")
    void generateBackupCodes_shouldReturnCorrectCountAndFormat() {
        List<String> codes = totpService.generateBackupCodes(8);

        assertThat(codes).hasSize(8);
        for (String code : codes) {
            assertThat(code).matches("^[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$");
        }
    }
}
