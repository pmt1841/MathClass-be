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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GoogleGeminiProviderStrategy implements AiProviderStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE;
    }

    @Override
    public AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt)
            throws Exception {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com/v1beta";

        if (!baseUrlStr.contains("/v1beta") && !baseUrlStr.contains("/v1")) {
            baseUrlStr = baseUrlStr + "/v1beta";
        }

        String rawModel = config.getModel() != null ? config.getModel() : "gemini-1.5-flash";
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;
        String targetUrl = baseUrlStr + "/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> generationConfig = new java.util.HashMap<>();
        if (config.getTemperature() != null) {
            generationConfig.put("temperature", config.getTemperature().doubleValue());
        }
        if (config.getMaxToken() != null) {
            generationConfig.put("maxOutputTokens", config.getMaxToken());
        }
        if (prompt != null && (prompt.contains("JSON") || prompt.contains("json"))) {
            generationConfig.put("responseMimeType", "application/json");
        }

        Map<String, Object> geminiPayload = new java.util.HashMap<>();
        geminiPayload.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))));
        if (!generationConfig.isEmpty()) {
            geminiPayload.put("generationConfig", generationConfig);
        }

        String reqBody = objectMapper.writeValueAsString(geminiPayload);
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String content = parts.get(0).path("text").asText("");
                    Integer completionTokens = parseCandidatesTokenCount(root);
                    return new AiExecutionResult(content, completionTokens);
                }
            }
            log.error("Google Gemini Provider returned HTTP status {} but invalid payload: {}", status,
                    response.body());
            throw new AiGenerationException(status, "Google Gemini Provider phản hồi không đúng cấu trúc dữ liệu");
        } else {
            log.error("Google Gemini Provider HTTP error status {}: {}", status, response.body());
            throw new AiGenerationException(status, "Gemini API returned HTTP " + status + ": " + response.body());
        }
    }

    @Override
    public AiExecutionResult executePromptWithImage(Provider provider, TaskConfig config, String apiKey,
                                                    String prompt, String base64Image, String mimeType) throws Exception {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return executePrompt(provider, config, apiKey, prompt);
        }

        String cleanBase64 = base64Image.contains(",")
                ? base64Image.substring(base64Image.indexOf(",") + 1)
                : base64Image;

        String actualMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "image/png";

        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com/v1beta";

        if (!baseUrlStr.contains("/v1beta") && !baseUrlStr.contains("/v1")) {
            baseUrlStr = baseUrlStr + "/v1beta";
        }

        String model = config.getModel() != null ? config.getModel() : "gemini-1.5-flash";
        String targetUrl = baseUrlStr + "/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of("inline_data", Map.of(
                "mime_type", actualMime,
                "data", cleanBase64
        ));

        Map<String, Object> geminiPayload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(textPart, imagePart))
                )
        );

        String reqBody = objectMapper.writeValueAsString(geminiPayload);
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String content = parts.get(0).path("text").asText("");
                    Integer completionTokens = parseCandidatesTokenCount(root);
                    return new AiExecutionResult(content, completionTokens);
                }
            }
            log.error("Google Gemini Provider returned HTTP status {} but invalid payload: {}", status, response.body());
            throw new AiGenerationException(status, "Google Gemini Provider phản hồi không đúng cấu trúc dữ liệu");
        } else {
            log.error("Google Gemini Provider HTTP error status {}: {}", status, response.body());
            throw new AiGenerationException(status, "Gemini API returned HTTP " + status + ": " + response.body());
        }
    }

    /**
     * Đọc số token đầu ra từ response Gemini:
     * {@code usageMetadata.candidatesTokenCount}. Trả về {@code null} khi thiếu
     * thông tin.
     */
    static Integer parseCandidatesTokenCount(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode usage = root.path("usageMetadata");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        int count = usage.path("candidatesTokenCount").asInt(0);
        if (count == 0) {
            count = usage.path("candidates_token_count").asInt(0);
        }
        return count > 0 ? count : null;
    }
}
