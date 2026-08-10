package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.ProviderUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ProviderResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.impl.ProviderServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ProviderServiceImpl providerService;

    private Provider sampleProvider;

    @BeforeEach
    void setUp() {
        sampleProvider = Provider.builder()
                .code("OPENAI")
                .name("OpenAI API")
                .baseUrl("https://api.openai.com/v1")
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .status(ProviderStatus.ACTIVE)
                .build();
        sampleProvider.setId(1L);
    }

    @Test
    @DisplayName("Tự động chuyển tất cả API Keys thành INACTIVE khi Provider chuyển sang INACTIVE")
    void testUpdateProvider_StatusInactive_SyncsApiKeysToInactive() {
        ProviderUpdateRequest request = ProviderUpdateRequest.builder()
                .name("OpenAI API")
                .baseUrl("https://api.openai.com/v1")
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .status(ProviderStatus.INACTIVE)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(sampleProvider));
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));

        ProviderResponse response = providerService.updateProvider(1L, request);

        assertEquals(ProviderStatus.INACTIVE, response.getStatus());
        verify(apiKeyRepository).updateStatusByProviderId(1L, ApiKeyStatus.INACTIVE);
    }

    @Test
    @DisplayName("Tự động chuyển tất cả API Keys thành ACTIVE khi Provider chuyển sang ACTIVE")
    void testUpdateProvider_StatusActive_SyncsApiKeysToActive() {
        sampleProvider.setStatus(ProviderStatus.INACTIVE);

        ProviderUpdateRequest request = ProviderUpdateRequest.builder()
                .name("OpenAI API")
                .baseUrl("https://api.openai.com/v1")
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .status(ProviderStatus.ACTIVE)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(sampleProvider));
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));

        ProviderResponse response = providerService.updateProvider(1L, request);

        assertEquals(ProviderStatus.ACTIVE, response.getStatus());
        verify(apiKeyRepository).updateStatusByProviderId(1L, ApiKeyStatus.ACTIVE);
    }
}
