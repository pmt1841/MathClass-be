package com.codegym.mathclass.aiconfig.strategy.impl;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicProviderStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnthropicProviderStrategy strategy = new AnthropicProviderStrategy();

    private JsonNode parse(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("Should support ANTHROPIC_COMPATIBLE protocol")
    void supports_AnthropicCompatible_ReturnsTrue() {
        assertThat(strategy.supports(ProviderProtocol.ANTHROPIC_COMPATIBLE)).isTrue();
        assertThat(strategy.supports(ProviderProtocol.OPENAI_COMPATIBLE)).isFalse();
        assertThat(strategy.supports(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).isFalse();
        assertThat(strategy.supports(ProviderProtocol.CUSTOM_REST)).isFalse();
    }

    @Test
    @DisplayName("Should parse usage.output_tokens correctly")
    void parseOutputTokens_ValidUsage_ReturnsOutputTokens() throws Exception {
        JsonNode root = parse("{\"usage\": {\"input_tokens\": 120, \"output_tokens\": 345}}");
        assertThat(AnthropicProviderStrategy.parseOutputTokens(root)).isEqualTo(345);
    }

    @Test
    @DisplayName("Should return null when usage is missing or zero")
    void parseOutputTokens_MissingOrZero_ReturnsNull() throws Exception {
        assertThat(AnthropicProviderStrategy.parseOutputTokens(parse("{\"content\": []}"))).isNull();
        assertThat(AnthropicProviderStrategy.parseOutputTokens(parse("{\"usage\": {}}"))).isNull();
        assertThat(AnthropicProviderStrategy.parseOutputTokens(parse("{\"usage\": {\"output_tokens\": 0}}"))).isNull();
        assertThat(AnthropicProviderStrategy.parseOutputTokens(null)).isNull();
    }
}
