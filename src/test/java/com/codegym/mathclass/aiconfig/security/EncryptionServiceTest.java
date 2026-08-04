package com.codegym.mathclass.aiconfig.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("TestMasterSecretKey32BytesLength!");
    }

    @Test
    @DisplayName("TC-SEC-01: Mã hóa và giải mã thành công bảo toàn dữ liệu ban đầu")
    void testEncryptAndDecrypt_Success() {
        String rawKey = "AIzaSyD8xK9mP2wQ1vR3tY5uI7oO4x9K4";
        String encrypted = encryptionService.encrypt(rawKey);

        assertNotNull(encrypted);
        assertNotEquals(rawKey, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(rawKey, decrypted);
    }

    @Test
    @DisplayName("TC-SEC-02: Giải mã chuỗi bị sửa đổi sẽ ném ngoại lệ")
    void testDecrypt_CorruptedTag_ThrowsException() {
        String rawKey = "AIzaSyD8xK9mP2wQ1vR3tY5uI7oO4x9K4";
        String encrypted = encryptionService.encrypt(rawKey);
        String corrupted = encrypted.substring(0, encrypted.length() - 4) + "XXXX";

        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(corrupted));
    }
}
