package com.codegym.mathclass.aiconfig.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        MasterKeyProvider keyProvider = new EnvVarMasterKeyProvider("TestMasterSecretKey32BytesLength!");
        encryptionService = new EncryptionService(keyProvider);
    }

    @Test
    @DisplayName("TC-SEC-01: Mã hóa và giải mã thành công bảo toàn dữ liệu ban đầu với MasterKeyProvider")
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

    @Test
    @DisplayName("TC-SEC-03: Xử lý chuỗi plaintext null hoặc rỗng trả về chính nó")
    void testEncryptDecrypt_NullOrEmpty() {
        assertNull(encryptionService.encrypt(null));
        assertEquals("", encryptionService.encrypt(""));
        assertNull(encryptionService.decrypt(null));
        assertEquals("", encryptionService.decrypt(""));
    }

    @Test
    @DisplayName("TC-SEC-04: Constructor với chuỗi khóa ngắn hoặc dài tự động chuẩn hóa 32 bytes")
    void testKeyLengthNormalization() {
        EncryptionService shortKeyService = new EncryptionService(new EnvVarMasterKeyProvider("shortKey"));
        String encrypted = shortKeyService.encrypt("Hello World");
        assertEquals("Hello World", shortKeyService.decrypt(encrypted));

        EncryptionService longKeyService = new EncryptionService(new EnvVarMasterKeyProvider("ThisIsAVeryLongMasterKeyThatExceedsThirtyTwoBytesInLength!"));
        String encrypted2 = longKeyService.encrypt("Testing Long Key");
        assertEquals("Testing Long Key", longKeyService.decrypt(encrypted2));
    }
}
