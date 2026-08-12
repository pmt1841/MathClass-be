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
public class OpenAiProviderStrategy implements AiProviderStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.OPENAI_COMPATIBLE || protocol == null;
    }

    @Override
    public AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt)
            throws Exception {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://api.openai.com/v1";

        if (!baseUrlStr.endsWith("/chat/completions")) {
            baseUrlStr = baseUrlStr.endsWith("/") ? baseUrlStr + "chat/completions" : baseUrlStr + "/chat/completions";
        }

        String rawModel = config.getModel() != null ? config.getModel() : "gpt-3.5-turbo";
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        Map<String, Object> openAiPayload = new HashMap<>();
        openAiPayload.put("model", model);
        openAiPayload.put("messages", List.of(
                Map.of("role", "user", "content", prompt)));
        openAiPayload.put("max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 512);
        openAiPayload.put("temperature", config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.4);

        if (prompt != null && (prompt.contains("JSON") || prompt.contains("json"))) {
            openAiPayload.put("response_format", Map.of("type", "json_object"));
        }

        String reqBody = objectMapper.writeValueAsString(openAiPayload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(baseUrlStr))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody));

        if (provider.getAuthHeaderName() != null && !provider.getAuthHeaderName().trim().isEmpty()) {
            String prefix = provider.getAuthHeaderPrefix() != null ? provider.getAuthHeaderPrefix().trim() + " " : "";
            reqBuilder.header(provider.getAuthHeaderName().trim(), prefix + apiKey);
        } else {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                if (message.has("content")) {
                    String content = message.path("content").asText("");
                    Integer completionTokens = parseCompletionTokens(root);
                    return new AiExecutionResult(content, completionTokens);
                }
            }
            log.error("OpenAI Compatible Provider returned HTTP status {} but invalid payload: {}", status,
                    response.body());
            throw new AiGenerationException(status, "AI Provider phản hồi không đúng cấu trúc dữ liệu");
        } else {
            log.error("OpenAI Compatible Provider HTTP error status {}: {}", status, response.body());
            throw new AiGenerationException(status, "OpenAI API returned HTTP " + status + ": " + response.body());
        }
    }

    @Override
    public AiExecutionResult executePromptWithImage(Provider provider, TaskConfig config, String apiKey,
                                                    String prompt, String base64Image, String mimeType) throws Exception {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return executePrompt(provider, config, apiKey, prompt);
        }

        String actualMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "image/png";
        String imageUrl = base64Image.startsWith("data:")
                ? base64Image
                : "data:" + actualMime + ";base64," + base64Image;

        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://api.openai.com/v1";

        String model = config.getModel() != null ? config.getModel() : "gpt-4o-mini";
        String targetUrl = baseUrlStr + "/chat/completions";

        List<Map<String, Object>> userContent = List.of(
                Map.of("type", "text", "text", prompt),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        );

        Map<String, Object> openAiPayload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "Bạn là trợ lý toán học."),
                        Map.of("role", "user", "content", userContent)
                ),
                "max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 1024,
                "temperature", config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.4
        );

        String reqBody = objectMapper.writeValueAsString(openAiPayload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody));

        if (provider.getAuthHeaderName() != null && !provider.getAuthHeaderName().trim().isEmpty()) {
            String prefix = provider.getAuthHeaderPrefix() != null ? provider.getAuthHeaderPrefix().trim() + " " : "";
            reqBuilder.header(provider.getAuthHeaderName().trim(), prefix + apiKey);
        } else {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                if (message.has("content")) {
                    String content = message.path("content").asText("");
                    Integer completionTokens = parseCompletionTokens(root);
                    return new AiExecutionResult(content, completionTokens);
                }
            }
            log.error("OpenAI Compatible Provider returned HTTP status {} but invalid payload: {}", status, response.body());
            throw new RuntimeException("AI Provider phản hồi không đúng cấu trúc dữ liệu");
        } else {
            log.error("OpenAI Compatible Provider HTTP error status {}: {}", status, response.body());
            throw new RuntimeException("AI Provider phản hồi lỗi HTTP " + status);
        }
    }

    /**
     * Đọc số token đầu ra từ response OpenAI-compatible:
     * ưu tiên {@code usage.completion_tokens}, fallback
     * {@code usage.output_tokens}.
     * Trả về {@code null} khi không có thông tin usage.
     */
    static Integer parseCompletionTokens(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        int completion = usage.path("completion_tokens").asInt(0);
        if (completion == 0) {
            completion = usage.path("output_tokens").asInt(0);
        }
        return completion > 0 ? completion : null;
    }
}
