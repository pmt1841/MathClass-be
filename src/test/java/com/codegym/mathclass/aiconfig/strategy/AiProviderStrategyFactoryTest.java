package com.codegym.mathclass.aiconfig.strategy;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.strategy.impl.AnthropicProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.impl.CustomAIProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.impl.GoogleGeminiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.impl.OpenAiProviderStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderStrategyFactoryTest {

    private final OpenAiProviderStrategy openAiStrategy = new OpenAiProviderStrategy();
    private final GoogleGeminiProviderStrategy geminiStrategy = new GoogleGeminiProviderStrategy();
    private final AnthropicProviderStrategy anthropicStrategy = new AnthropicProviderStrategy();
    private final CustomAIProviderStrategy customAiStrategy = new CustomAIProviderStrategy();

    private final AiProviderStrategyFactory factory = new AiProviderStrategyFactory(
            List.of(openAiStrategy, geminiStrategy, anthropicStrategy, customAiStrategy)
    );

    @Test
    @DisplayName("Should resolve AnthropicProviderStrategy for ANTHROPIC_COMPATIBLE")
    void getStrategy_AnthropicCompatible_ReturnsAnthropicStrategy() {
        AiProviderStrategy strategy = factory.getStrategy(ProviderProtocol.ANTHROPIC_COMPATIBLE);
        assertThat(strategy).isInstanceOf(AnthropicProviderStrategy.class);
    }

    @Test
    @DisplayName("Should resolve OpenAiProviderStrategy for OPENAI_COMPATIBLE")
    void getStrategy_OpenAiCompatible_ReturnsOpenAiStrategy() {
        AiProviderStrategy strategy = factory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE);
        assertThat(strategy).isInstanceOf(OpenAiProviderStrategy.class);
    }

    @Test
    @DisplayName("Should resolve GoogleGeminiProviderStrategy for GOOGLE_GEMINI_COMPATIBLE")
    void getStrategy_GeminiCompatible_ReturnsGeminiStrategy() {
        AiProviderStrategy strategy = factory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE);
        assertThat(strategy).isInstanceOf(GoogleGeminiProviderStrategy.class);
    }

    @Test
    @DisplayName("Should resolve CustomAIProviderStrategy for CUSTOM_REST")
    void getStrategy_CustomRest_ReturnsCustomAiStrategy() {
        AiProviderStrategy strategy = factory.getStrategy(ProviderProtocol.CUSTOM_REST);
        assertThat(strategy).isInstanceOf(CustomAIProviderStrategy.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when no strategy matches protocol")
    void getStrategy_UnsupportedProtocol_ThrowsException() {
        AiProviderStrategyFactory emptyFactory = new AiProviderStrategyFactory(List.of());
        assertThatThrownBy(() -> emptyFactory.getStrategy(ProviderProtocol.CUSTOM_REST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chưa hỗ trợ giao thức AI Provider: CUSTOM_REST");
    }
}
