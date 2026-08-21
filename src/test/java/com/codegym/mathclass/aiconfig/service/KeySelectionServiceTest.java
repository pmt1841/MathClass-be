package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeySelectionServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private KeySelectionService keySelectionService;

    private Provider priorityProvider;
    private Provider roundRobinProvider;
    private ApiKey key1;
    private ApiKey key2;

    @BeforeEach
    void setUp() {
        priorityProvider = Provider.builder()
                .code("GEMINI")
                .name("Google Gemini")
                .strategy(ProviderStrategy.PRIORITY)
                .build();
        priorityProvider.setId(1L);

        roundRobinProvider = Provider.builder()
                .code("OPENAI")
                .name("OpenAI")
                .strategy(ProviderStrategy.ROUND_ROBIN)
                .build();
        roundRobinProvider.setId(2L);

        key1 = ApiKey.builder().name("Key 1").priority(10).status(ApiKeyStatus.ACTIVE).build();
        key1.setId(101L);
        key2 = ApiKey.builder().name("Key 2").priority(5).status(ApiKeyStatus.ACTIVE).build();
        key2.setId(102L);
    }

    @Test
    @DisplayName("TC-KEY-01: PRIORITY Strategy chọn Key có priority cao nhất")
    void testPriorityStrategy_SelectsHighestPriorityKey() {
        when(apiKeyRepository.findByProviderIdAndStatusOrderByPriorityDesc(eq(1L), eq(ApiKeyStatus.ACTIVE)))
                .thenReturn(List.of(key1, key2));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKey selected = keySelectionService.selectKeyForProvider(priorityProvider);
        assertNotNull(selected);
        assertEquals(101L, selected.getId());
    }

    @Test
    @DisplayName("TC-KEY-03: ROUND_ROBIN Strategy luân phiên các Active Keys")
    void testRoundRobinStrategy_CyclesKeys() {
        when(apiKeyRepository.findByProviderIdAndStatusOrderByPriorityDesc(eq(2L), eq(ApiKeyStatus.ACTIVE)))
                .thenReturn(List.of(key1, key2));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKey selectedFirst = keySelectionService.selectKeyForProvider(roundRobinProvider);
        ApiKey selectedSecond = keySelectionService.selectKeyForProvider(roundRobinProvider);

        assertNotNull(selectedFirst);
        assertNotNull(selectedSecond);
        assertNotEquals(selectedFirst.getId(), selectedSecond.getId());
    }

    @Test
    @DisplayName("TC-KEY-04: Thói quen ném ngoại lệ khi không có Key khả dụng")
    void testNoAvailableKeys_ThrowsException() {
        when(apiKeyRepository.findByProviderIdAndStatusOrderByPriorityDesc(eq(1L), eq(ApiKeyStatus.ACTIVE)))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> keySelectionService.selectKeyForProvider(priorityProvider));
    }

    @Test
    @DisplayName("TC-KEY-05: Cooldown key và kiểm tra thời gian còn lại")
    void testCooldownKey_TracksRemainingSecondsAndExpiresAt() {
        keySelectionService.cooldownKey(101L, 300);

        Long remaining = keySelectionService.getCooldownRemainingSeconds(101L);
        assertNotNull(remaining);
        assertTrue(remaining > 280 && remaining <= 300);

        assertNotNull(keySelectionService.getCooldownExpiresAt(101L));

        // Key 102 không có cooldown
        assertNull(keySelectionService.getCooldownRemainingSeconds(102L));
        assertNull(keySelectionService.getCooldownExpiresAt(102L));

        // Xóa cooldown
        keySelectionService.clearCooldown(101L);
        assertNull(keySelectionService.getCooldownRemainingSeconds(101L));
        assertNull(keySelectionService.getCooldownExpiresAt(101L));
    }
}
