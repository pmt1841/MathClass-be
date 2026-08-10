package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.TestConnectionRequest;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionTestServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ConnectionTestService connectionTestService;

    private Provider mockProvider;
    private ApiKey mockApiKey;

    @BeforeEach
    void setUp() {
        mockProvider = Provider.builder()
                .code("GEMINI")
                .name("Google Gemini")
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .healthCheckPath("/models")
                .build();
        mockProvider.setId(10L);

        mockApiKey = ApiKey.builder()
                .name("Default Key")
                .encryptedKey("AIzaSyD8xK9mP2wQ1vR3tY5uI7oO4x9K4")
                .status(ApiKeyStatus.ACTIVE)
                .provider(mockProvider)
                .build();
        mockApiKey.setId(100L);
    }

    @Test
    @DisplayName("TC-CONN-01: Kiểm tra kết nối báo lỗi khi API Key bị rỗng")
    void testConnection_EmptyApiKey_ReturnsInvalidKeyResponse() {
        TestConnectionRequest request = TestConnectionRequest.builder()
                .providerCode("GEMINI")
                .apiKey("")
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .build();

        TestConnectionResponse response = connectionTestService.testConnection(request);

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertFalse(response.getValid());
        assertEquals("INVALID_KEY", response.getErrorCode());
        assertEquals("API Key không được để trống", response.getMessage());
    }

    @Test
    @DisplayName("TC-CONN-02: verifyKey ném ngoại lệ khi API Key ID không tồn tại")
    void testVerifyKey_NotFound_ThrowsException() {
        when(apiKeyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> connectionTestService.verifyKey(999L));
    }

    @Test
    @DisplayName("TC-CONN-02B: verifyKey tự động chuyển trạng thái API Key sang INACTIVE khi kết nối thất bại")
    void testVerifyKey_Failure_AutoSetsInactive() {
        Provider provider = Provider.builder()
                .code("OPENAI")
                .baseUrl("https://invalid.openai.domain")
                .protocol(com.codegym.mathclass.aiconfig.entity.ProviderProtocol.OPENAI_COMPATIBLE)
                .build();

        ApiKey apiKey = ApiKey.builder()
                .encryptedKey("sk-invalidkey")
                .provider(provider)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        apiKey.setId(10L);

        when(apiKeyRepository.findById(10L)).thenReturn(Optional.of(apiKey));

        TestConnectionResponse response = connectionTestService.verifyKey(10L);

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertEquals(ApiKeyStatus.INACTIVE, apiKey.getStatus());
        org.mockito.Mockito.verify(apiKeyRepository).save(apiKey);
    }

    @Test
    @DisplayName("TC-CONN-03: fetchAvailableModels trả về danh sách rỗng khi Provider không có API Key active")
    void testFetchAvailableModels_NoApiKeys_ReturnsEmptyList() {
        Provider emptyProvider = Provider.builder().code("CUSTOM").apiKeys(Collections.emptyList()).build();
        emptyProvider.setId(20L);

        when(providerRepository.findById(20L)).thenReturn(Optional.of(emptyProvider));

        List<String> models = connectionTestService.fetchAvailableModels(20L);
        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    @DisplayName("TC-CONN-04: fetchAvailableModels ném ngoại lệ khi Provider ID không tồn tại")
    void testFetchAvailableModels_ProviderNotFound_ThrowsException() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> connectionTestService.fetchAvailableModels(999L));
    }
}
