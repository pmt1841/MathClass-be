package com.codegym.mathclass.aiconfig.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleGeminiProviderStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("UT-BE-17: Should parse usageMetadata.candidatesTokenCount")
    void parseCandidatesTokenCount_fromCandidatesTokenCount() throws Exception {
        JsonNode root = parse("{\"usageMetadata\": {\"promptTokenCount\": 10, \"candidatesTokenCount\": 321}}");
        assertThat(GoogleGeminiProviderStrategy.parseCandidatesTokenCount(root)).isEqualTo(321);
    }

    @Test
    @DisplayName("Should fallback to snake_case candidates_token_count")
    void parseCandidatesTokenCount_snakeCaseFallback() throws Exception {
        JsonNode root = parse("{\"usageMetadata\": {\"candidates_token_count\": 55}}");
        assertThat(GoogleGeminiProviderStrategy.parseCandidatesTokenCount(root)).isEqualTo(55);
    }

    @Test
    @DisplayName("Should return null when usage missing")
    void parseCandidatesTokenCount_missing_returnsNull() throws Exception {
        assertThat(GoogleGeminiProviderStrategy.parseCandidatesTokenCount(parse("{\"candidates\": []}"))).isNull();
        assertThat(GoogleGeminiProviderStrategy.parseCandidatesTokenCount(null)).isNull();
    }
}
