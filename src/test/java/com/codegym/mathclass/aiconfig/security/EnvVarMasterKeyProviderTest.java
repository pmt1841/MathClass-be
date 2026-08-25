package com.codegym.mathclass.aiconfig.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvVarMasterKeyProviderTest {

    @Test
    @DisplayName("UT-BE-09: EnvVarMasterKeyProvider trả về đúng khóa được cấu hình")
    void getMasterKey_ReturnsConfiguredKey() {
        EnvVarMasterKeyProvider provider = new EnvVarMasterKeyProvider("MyCustomEnvVarSecretKey32BytesLong");
        assertEquals("MyCustomEnvVarSecretKey32BytesLong", provider.getMasterKey());
    }

    @Test
    @DisplayName("UT-BE-10: EnvVarMasterKeyProvider tự động sử dụng fallback key khi truyền null hoặc rỗng")
    void getMasterKey_WhenNullOrBlank_ReturnsDefaultFallbackKey() {
        EnvVarMasterKeyProvider providerNull = new EnvVarMasterKeyProvider(null);
        assertEquals("MathClassSecretKeyForAiEncryption32B!", providerNull.getMasterKey());

        EnvVarMasterKeyProvider providerBlank = new EnvVarMasterKeyProvider("   ");
        assertEquals("MathClassSecretKeyForAiEncryption32B!", providerBlank.getMasterKey());
    }
}
