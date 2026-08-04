package com.codegym.mathclass.aiconfig.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyCryptoConverterTest {

    private ApiKeyCryptoConverter converter;
    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("TestMasterSecretKey32BytesLength!");
        converter = new ApiKeyCryptoConverter();
        converter.setEncryptionService(encryptionService);
    }

    @Test
    @DisplayName("TC-CONV-01: Chuyển đổi thành công giữa Entity Attribute và Database Column")
    void testConvertToDatabaseColumnAndEntityAttribute_Success() {
        String plainKey = "sk-proj-test1234567890abcdef";

        String dbData = converter.convertToDatabaseColumn(plainKey);
        assertNotNull(dbData);
        assertNotEquals(plainKey, dbData);

        String decrypted = converter.convertToEntityAttribute(dbData);
        assertEquals(plainKey, decrypted);
    }

    @Test
    @DisplayName("TC-CONV-02: Xử lý mượt mà khi attribute null hoặc rỗng")
    void testNullOrEmptyAttribute_ReturnsSameValue() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals("", converter.convertToDatabaseColumn(""));

        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToEntityAttribute(""));
    }
}
