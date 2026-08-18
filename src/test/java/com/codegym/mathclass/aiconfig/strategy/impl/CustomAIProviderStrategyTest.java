package com.codegym.mathclass.aiconfig.strategy.impl;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAIProviderStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomAIProviderStrategy strategy = new CustomAIProviderStrategy();

    private JsonNode parse(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("Should support CUSTOM_REST protocol")
    void supports_CustomRest_ReturnsTrue() {
        assertThat(strategy.supports(ProviderProtocol.CUSTOM_REST)).isTrue();
        assertThat(strategy.supports(ProviderProtocol.OPENAI_COMPATIBLE)).isFalse();
        assertThat(strategy.supports(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).isFalse();
        assertThat(strategy.supports(ProviderProtocol.ANTHROPIC_COMPATIBLE)).isFalse();
    }

    @Test
    @DisplayName("Should extract content from OpenAI-style response")
    void extractContent_OpenAiStyle() throws Exception {
        String json = "{\"choices\": [{\"message\": {\"content\": \"Lời giải toán học\"}}]}";
        assertThat(CustomAIProviderStrategy.extractContent(parse(json), json)).isEqualTo("Lời giải toán học");
    }

    @Test
    @DisplayName("Should extract content from Anthropic-style response")
    void extractContent_AnthropicStyle() throws Exception {
        String json = "{\"content\": [{\"type\": \"text\", \"text\": \"Công thức LaTeX: $x^2$\"}]}";
        assertThat(CustomAIProviderStrategy.extractContent(parse(json), json)).isEqualTo("Công thức LaTeX: $x^2$");
    }

    @Test
    @DisplayName("Should extract content from Gemini-style response")
    void extractContent_GeminiStyle() throws Exception {
        String json = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Gợi ý bài toán\"}]}}]}";
        assertThat(CustomAIProviderStrategy.extractContent(parse(json), json)).isEqualTo("Gợi ý bài toán");
    }

    @Test
    @DisplayName("Should extract content from direct fields (FastAPI, Flask, Ollama)")
    void extractContent_DirectFields() throws Exception {
        assertThat(CustomAIProviderStrategy.extractContent(parse("{\"response\": \"Kết quả 1\"}"), "")).isEqualTo("Kết quả 1");
        assertThat(CustomAIProviderStrategy.extractContent(parse("{\"output\": \"Kết quả 2\"}"), "")).isEqualTo("Kết quả 2");
        assertThat(CustomAIProviderStrategy.extractContent(parse("{\"result\": \"Kết quả 3\"}"), "")).isEqualTo("Kết quả 3");
        assertThat(CustomAIProviderStrategy.extractContent(parse("{\"text\": \"Kết quả 4\"}"), "")).isEqualTo("Kết quả 4");
        assertThat(CustomAIProviderStrategy.extractContent(parse("{\"generated_text\": \"Kết quả 5\"}"), "")).isEqualTo("Kết quả 5");
    }

    @Test
    @DisplayName("Should parse completion tokens from usage or eval_count")
    void parseCompletionTokens_VariousFormats() throws Exception {
        JsonNode openAiUsage = parse("{\"usage\": {\"completion_tokens\": 128}}");
        assertThat(CustomAIProviderStrategy.parseCompletionTokens(openAiUsage)).isEqualTo(128);

        JsonNode anthropicUsage = parse("{\"usage\": {\"output_tokens\": 256}}");
        assertThat(CustomAIProviderStrategy.parseCompletionTokens(anthropicUsage)).isEqualTo(256);

        JsonNode ollamaUsage = parse("{\"eval_count\": 512}");
        assertThat(CustomAIProviderStrategy.parseCompletionTokens(ollamaUsage)).isEqualTo(512);

        JsonNode emptyUsage = parse("{\"usage\": {}}");
        assertThat(CustomAIProviderStrategy.parseCompletionTokens(emptyUsage)).isNull();
    }
}
