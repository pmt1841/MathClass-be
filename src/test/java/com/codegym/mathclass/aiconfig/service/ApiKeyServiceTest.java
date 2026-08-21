package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.ApiKeyCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyStatusPatchRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ApiKeyResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.service.impl.ApiKeyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private KeySelectionService keySelectionService;

    @InjectMocks
    private ApiKeyServiceImpl apiKeyService;

    private Provider provider;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        provider = Provider.builder().name("Google Gemini").code("GEMINI").build();
        provider.setId(1L);

        apiKey = ApiKey.builder()
                .provider(provider)
                .name("Gemini Key 1")
                .encryptedKey("AIzaSyD-1234567890abcdef")
                .priority(10)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        apiKey.setId(100L);
    }

    @Test
    @DisplayName("TC-APIKEY-01: Update Key Priority và Name thành công")
    void testUpdateKeyPriorityAndName() {
        when(apiKeyRepository.findById(100L)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyUpdateRequest updateRequest = ApiKeyUpdateRequest.builder()
                .name("Gemini Key Updated")
                .priority(25)
                .build();

        ApiKeyResponse response = apiKeyService.updateKey(100L, updateRequest);

        assertNotNull(response);
        assertEquals("Gemini Key Updated", response.getName());
        assertEquals(25, response.getPriority());
        assertEquals(ApiKeyStatus.ACTIVE, response.getStatus());
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    @DisplayName("TC-APIKEY-02: Update Key Status thành công")
    void testUpdateKeyStatus() {
        when(apiKeyRepository.findById(100L)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyStatusPatchRequest patchRequest = new ApiKeyStatusPatchRequest();
        patchRequest.setStatus(ApiKeyStatus.INACTIVE);

        ApiKeyResponse response = apiKeyService.updateKeyStatus(100L, patchRequest);

        assertNotNull(response);
        assertEquals(ApiKeyStatus.INACTIVE, response.getStatus());
    }
}
