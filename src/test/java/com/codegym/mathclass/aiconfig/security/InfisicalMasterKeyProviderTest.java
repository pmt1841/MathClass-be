package com.codegym.mathclass.aiconfig.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfisicalMasterKeyProviderTest {

    @Mock
    private InfisicalClient infisicalClient;

    private InfisicalMasterKeyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InfisicalMasterKeyProvider(infisicalClient);
    }

    @Test
    @DisplayName("UT-BE-07: In-Memory Caching: Gọi getMasterKey nhiều lần chỉ fetch từ InfisicalClient 1 lần duy nhất")
    void getMasterKey_CachesInMemory() {
        when(infisicalClient.fetchMasterKey()).thenReturn("CachedMasterSecretKey32BytesLong!");

        // Gọi 5 lần
        for (int i = 0; i < 5; i++) {
            String key = provider.getMasterKey();
            assertEquals("CachedMasterSecretKey32BytesLong!", key);
        }

        // Kiểm tra infisicalClient chỉ được gọi 1 lần duy nhất
        verify(infisicalClient, times(1)).fetchMasterKey();
    }

    @Test
    @DisplayName("UT-BE-08: init() nạp key eager lúc startup")
    void init_EagerlyLoadsKey() {
        when(infisicalClient.fetchMasterKey()).thenReturn("EagerLoadedSecretKey32BytesLong!");

        provider.init();

        verify(infisicalClient, times(1)).fetchMasterKey();
        assertEquals("EagerLoadedSecretKey32BytesLong!", provider.getMasterKey());
        verify(infisicalClient, times(1)).fetchMasterKey();
    }
}
