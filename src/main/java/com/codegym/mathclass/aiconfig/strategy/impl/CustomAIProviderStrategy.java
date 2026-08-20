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

/**
 * Chiến lược kết nối cho các dịch vụ AI REST tùy chỉnh (Self-hosted AI,
 * FastAPI, Flask, Custom LLM Server).
 *
 * <p>
 * Cung cấp khả năng thích ứng linh hoạt (Generic Adapter):
 * <ul>
 * <li>Tùy chỉnh Header xác thực hoặc Query Param theo cấu hình Provider.</li>
 * <li>Gửi payload đa năng (tương thích cả completion và chat formats).</li>
 * <li>Trích xuất kết quả thông minh (Smart Response Extraction) từ các định
 * dạng output phổ biến.</li>
 * </ul>
 */
@Slf4j
@Component
public class CustomAIProviderStrategy implements AiProviderStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.CUSTOM_REST;
    }

    @Override
    public AiExecutionResult executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt)
            throws Exception {
        String targetUrl = resolveTargetUrl(provider, apiKey);

        String rawModel = config.getModel() != null && !config.getModel().trim().isEmpty()
                ? config.getModel().trim()
                : "custom-model";
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt != null ? prompt : "");
        payload.put("messages", List.of(
                Map.of("role", "user", "content", prompt != null ? prompt : "")));
        payload.put("max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 2048);
        if (config.getTemperature() != null) {
            payload.put("temperature", config.getTemperature().doubleValue());
        }

        return sendCustomRequest(targetUrl, apiKey, provider, payload);
    }

    @Override
    public AiExecutionResult executePromptWithImage(Provider provider, TaskConfig config, String apiKey,
            String prompt, String base64Image, String mimeType) throws Exception {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return executePrompt(provider, config, apiKey, prompt);
        }

        String targetUrl = resolveTargetUrl(provider, apiKey);

        String rawModel = config.getModel() != null && !config.getModel().trim().isEmpty()
                ? config.getModel().trim()
                : "custom-vision-model";
        String model = rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        String actualMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "image/png";
        String cleanBase64 = base64Image;
        if (cleanBase64.contains(",")) {
            cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
        }
        cleanBase64 = cleanBase64.trim();
        String dataUri = "data:" + actualMime + ";base64," + cleanBase64;

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt != null ? prompt : "");
        payload.put("image", cleanBase64);
        payload.put("mimeType", actualMime);
        payload.put("messages", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt != null ? prompt : ""),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));
        payload.put("max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 2048);
        if (config.getTemperature() != null) {
            payload.put("temperature", config.getTemperature().doubleValue());
        }

        return sendCustomRequest(targetUrl, apiKey, provider, payload);
    }

    private AiExecutionResult sendCustomRequest(String url, String apiKey, Provider provider,
            Map<String, Object> payload)
            throws Exception {
        String reqBody = objectMapper.writeValueAsString(payload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(120))
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody));

        // Áp dụng Header xác thực theo cấu hình Provider
        if (provider.getAuthHeaderName() != null && !provider.getAuthHeaderName().trim().isEmpty()) {
            String prefix = provider.getAuthHeaderPrefix() != null ? provider.getAuthHeaderPrefix().trim() + " " : "";
            reqBuilder.header(provider.getAuthHeaderName().trim(), prefix + (apiKey != null ? apiKey : ""));
        } else if (apiKey != null && !apiKey.isBlank()
                && (provider.getAuthQueryParam() == null || provider.getAuthQueryParam().isBlank())) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            String body = response.body();
            try {
                JsonNode root = objectMapper.readTree(body);
                String content = extractContent(root, body);
                if (content != null && !content.isBlank()) {
                    Integer completionTokens = parseCompletionTokens(root);
                    return new AiExecutionResult(content, completionTokens);
                }
            } catch (Exception e) {
                // Nếu phản hồi là plain text thuần túy không phải JSON
                if (body != null && !body.isBlank()) {
                    return new AiExecutionResult(body.trim(), null);
                }
            }

            log.error("Custom AI Provider returned HTTP status {} but unparseable payload: {}", status,
                    response.body());
            throw new AiGenerationException(status,
                    "Custom AI Provider phản hồi dữ liệu rỗng hoặc không đúng định dạng");
        } else {
            String errorMessage = extractErrorMessage(response.body(), status);
            log.error("Custom AI Provider HTTP error status {}: {}", status, response.body());
            throw new AiGenerationException(status, errorMessage);
        }
    }

    private String resolveTargetUrl(Provider provider, String apiKey) {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim()
                : "";

        if (provider.getAuthQueryParam() != null && !provider.getAuthQueryParam().trim().isEmpty() && apiKey != null
                && !apiKey.isBlank()) {
            String paramName = provider.getAuthQueryParam().trim();
            String separator = baseUrlStr.contains("?") ? "&" : "?";
            baseUrlStr = baseUrlStr + separator + paramName + "=" + apiKey;
        }
        return baseUrlStr;
    }

    /**
     * Tự động quét và trích xuất nội dung văn bản từ các cấu trúc JSON AI phổ biến.
     */
    static String extractContent(JsonNode root, String rawBody) {
        if (root == null) {
            return rawBody;
        }

        if (root.isTextual()) {
            return root.asText();
        }

        // 1. OpenAI-style: choices[0].message.content hoặc choices[0].text
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice0 = choices.get(0);
            if (choice0.has("message") && choice0.path("message").has("content")) {
                return choice0.path("message").path("content").asText();
            }
            if (choice0.has("text")) {
                return choice0.path("text").asText();
            }
        }

        // 2. Anthropic-style: content[0].text
        JsonNode contentArr = root.path("content");
        if (contentArr.isArray() && !contentArr.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : contentArr) {
                if ("text".equals(block.path("type").asText("text")) || block.has("text")) {
                    sb.append(block.path("text").asText(""));
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }

        // 3. Gemini-style: candidates[0].content.parts[0].text
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText();
            }
        }

        // 4. Single standard fields (FastAPI, HuggingFace, Ollama, Custom REST)
        String[] possibleFields = { "response", "output", "text", "result", "content", "generated_text", "data" };
        for (String field : possibleFields) {
            if (root.has(field) && root.get(field).isTextual()) {
                return root.get(field).asText();
            }
        }

        // 5. HuggingFace array format: [{"generated_text": "..."}]
        if (root.isArray() && !root.isEmpty()) {
            JsonNode item0 = root.get(0);
            for (String field : possibleFields) {
                if (item0.has(field) && item0.get(field).isTextual()) {
                    return item0.get(field).asText();
                }
            }
        }

        return null;
    }

    /**
     * Đọc số token đầu ra từ response của Custom AI (hỗ trợ OpenAI, Anthropic,
     * Ollama format).
     */
    static Integer parseCompletionTokens(JsonNode root) {
        if (root == null) {
            return null;
        }

        // OpenAI / Custom format: usage.completion_tokens hoặc usage.output_tokens
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode() && !usage.isNull()) {
            int completion = usage.path("completion_tokens").asInt(0);
            if (completion == 0) {
                completion = usage.path("output_tokens").asInt(0);
            }
            if (completion == 0) {
                completion = usage.path("total_tokens").asInt(0);
            }
            if (completion > 0)
                return completion;
        }

        // Ollama format: eval_count
        if (root.has("eval_count") && root.path("eval_count").asInt(0) > 0) {
            return root.path("eval_count").asInt();
        }

        return null;
    }

    private String extractErrorMessage(String responseBody, int status) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error") && root.path("error").has("message")) {
                return "Custom AI: " + root.path("error").path("message").asText();
            }
            if (root.has("detail")) { // FastAPI validation error
                return "Custom AI (FastAPI): " + root.path("detail").asText();
            }
            if (root.has("message")) {
                return "Custom AI: " + root.path("message").asText();
            }
        } catch (Exception ignored) {
        }
        return "Custom AI API returned HTTP " + status + ": " + responseBody;
    }
}
