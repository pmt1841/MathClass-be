package com.codegym.mathclass.aiconfig.strategy.impl;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AnthropicProviderStrategy implements AiProviderStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.ANTHROPIC_COMPATIBLE;
    }

    @Override
    public AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt)
            throws Exception {
        String baseUrlStr = resolveBaseUrl(provider);

        String rawModel = config.getModel() != null && !config.getModel().trim().isEmpty()
                ? config.getModel().trim()
                : "claude-3-5-sonnet-20241022";
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "user", "content", prompt != null ? prompt : "")));
        payload.put("max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 1024);
        if (config.getTemperature() != null) {
            payload.put("temperature", config.getTemperature().doubleValue());
        }

        return sendAnthropicRequest(baseUrlStr, apiKey, provider, payload);
    }

    @Override
    public AiExecutionResult executePromptWithImage(Provider provider, TaskConfig config, String apiKey,
                                                    String prompt, String base64Image, String mimeType) throws Exception {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return executePrompt(provider, config, apiKey, prompt);
        }

        String baseUrlStr = resolveBaseUrl(provider);

        String rawModel = config.getModel() != null && !config.getModel().trim().isEmpty()
                ? config.getModel().trim()
                : "claude-3-5-sonnet-20241022";
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        String actualMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "image/png";
        String cleanBase64 = base64Image;
        if (cleanBase64.contains(",")) {
            cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
        }
        cleanBase64 = cleanBase64.trim();

        List<Map<String, Object>> contentBlocks = List.of(
                Map.of(
                        "type", "image",
                        "source", Map.of(
                                "type", "base64",
                                "media_type", actualMime,
                                "data", cleanBase64
                        )
                ),
                Map.of(
                        "type", "text",
                        "text", prompt != null ? prompt : ""
                )
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "user", "content", contentBlocks)));
        payload.put("max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 1024);
        if (config.getTemperature() != null) {
            payload.put("temperature", config.getTemperature().doubleValue());
        }

        return sendAnthropicRequest(baseUrlStr, apiKey, provider, payload);
    }

    private AiExecutionResult sendAnthropicRequest(String url, String apiKey, Provider provider, Map<String, Object> payload)
            throws Exception {
        String reqBody = objectMapper.writeValueAsString(payload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody));

        if (provider.getAuthHeaderName() != null && !provider.getAuthHeaderName().trim().isEmpty()) {
            String prefix = provider.getAuthHeaderPrefix() != null ? provider.getAuthHeaderPrefix().trim() + " " : "";
            reqBuilder.header(provider.getAuthHeaderName().trim(), prefix + apiKey);
        } else {
            reqBuilder.header("x-api-key", apiKey);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentArr = root.path("content");
            if (contentArr.isArray() && !contentArr.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : contentArr) {
                    if ("text".equals(block.path("type").asText("text"))) {
                        sb.append(block.path("text").asText(""));
                    }
                }
                String content = sb.toString();
                Integer completionTokens = parseOutputTokens(root);
                return new AiExecutionResult(content, completionTokens);
            }
            log.error("Anthropic Provider returned HTTP status {} but invalid payload: {}", status, response.body());
            throw new AiGenerationException(status, "Anthropic Provider phản hồi không đúng cấu trúc dữ liệu");
        } else {
            String errorMessage = extractErrorMessage(response.body(), status);
            log.error("Anthropic Provider HTTP error status {}: {}", status, response.body());
            throw new AiGenerationException(status, errorMessage);
        }
    }

    private String resolveBaseUrl(Provider provider) {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://api.anthropic.com/v1";

        if (!baseUrlStr.endsWith("/messages")) {
            baseUrlStr = baseUrlStr.endsWith("/") ? baseUrlStr + "messages" : baseUrlStr + "/messages";
        }
        return baseUrlStr;
    }

    static Integer parseOutputTokens(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        int outputTokens = usage.path("output_tokens").asInt(0);
        return outputTokens > 0 ? outputTokens : null;
    }

    private String extractErrorMessage(String responseBody, int status) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error") && root.path("error").has("message")) {
                return "Anthropic API: " + root.path("error").path("message").asText();
            }
        } catch (Exception ignored) {
        }
        return "Anthropic API returned HTTP " + status + ": " + responseBody;
    }
}
