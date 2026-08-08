package com.codegym.mathclass.aiconfig.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiProviderStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("UT-BE-16: Should parse usage.completion_tokens")
    void parseCompletionTokens_fromCompletionTokens() throws Exception {
        JsonNode root = parse("{\"usage\": {\"prompt_tokens\": 10, \"completion_tokens\": 432, \"total_tokens\": 442}}");
        assertThat(OpenAiProviderStrategy.parseCompletionTokens(root)).isEqualTo(432);
    }

    @Test
    @DisplayName("Should fallback to usage.output_tokens")
    void parseCompletionTokens_fallbackOutputTokens() throws Exception {
        JsonNode root = parse("{\"usage\": {\"output_tokens\": 77}}");
        assertThat(OpenAiProviderStrategy.parseCompletionTokens(root)).isEqualTo(77);
    }

    @Test
    @DisplayName("Should return null when usage missing or zero")
    void parseCompletionTokens_missingUsage_returnsNull() throws Exception {
        assertThat(OpenAiProviderStrategy.parseCompletionTokens(parse("{\"choices\": []}"))).isNull();
        assertThat(OpenAiProviderStrategy.parseCompletionTokens(parse("{\"usage\": {}}"))).isNull();
        assertThat(OpenAiProviderStrategy.parseCompletionTokens(null)).isNull();
    }
}
