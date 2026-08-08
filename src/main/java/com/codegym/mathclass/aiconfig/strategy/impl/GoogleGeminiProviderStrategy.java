package com.codegym.mathclass.aiconfig.strategy.impl;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
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
    public AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt) throws Exception {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com/v1beta";

        if (!baseUrlStr.contains("/v1beta") && !baseUrlStr.contains("/v1")) {
            baseUrlStr = baseUrlStr + "/v1beta";
        }

        String model = config.getModel() != null ? config.getModel() : "gemini-1.5-flash";
        String targetUrl = baseUrlStr + "/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> geminiPayload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        String reqBody = objectMapper.writeValueAsString(geminiPayload);
        HttpRequest request = HttpRequest.newBuilder()
                // Timeout 120s: prompt dài (ví dụ chấm bài tự luận MAT-250 kèm dữ liệu hình vẽ Canvas)
                // cần thời gian generate lâu hơn nhiều so với 30s mặc định. Áp dụng cho mọi task AI.
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
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
            throw new RuntimeException("Google Gemini Provider phản hồi không đúng cấu trúc dữ liệu");
        } else {
            log.error("Google Gemini Provider HTTP error status {}: {}", status, response.body());
            throw new RuntimeException("Google Gemini Provider phản hồi lỗi HTTP " + status);
        }
    }

    /**
     * Đọc số token đầu ra từ response Gemini:
     * {@code usageMetadata.candidatesTokenCount}. Trả về {@code null} khi thiếu thông tin.
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
