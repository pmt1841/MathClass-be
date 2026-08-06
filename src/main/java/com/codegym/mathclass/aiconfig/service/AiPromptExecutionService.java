package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptExecutionService {

    private final TaskConfigRepository taskConfigRepository;
    private final KeySelectionService keySelectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public String executePrompt(String taskCode, String prompt) {
        Optional<TaskConfig> configOpt = taskConfigRepository.findByTask(taskCode);
        if (configOpt.isEmpty()) {
            log.warn("TaskConfig '{}' chưa được cấu hình. Sử dụng phản hồi fallback mặc định.", taskCode);
            return fallbackResponse(prompt);
        }

        TaskConfig config = configOpt.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("Tác vụ AI '{}' hiện đang bị vô hiệu hóa. Sử dụng phản hồi fallback.", taskCode);
            return fallbackResponse(prompt);
        }

        Provider provider = config.getProvider();
        if (provider == null || provider.getStatus() != com.codegym.mathclass.aiconfig.entity.ProviderStatus.ACTIVE) {
            log.warn("Provider AI cho tác vụ '{}' không khả dụng. Sử dụng phản hồi fallback.", taskCode);
            return fallbackResponse(prompt);
        }

        try {
            ApiKey apiKeyObj = keySelectionService.selectKeyForProvider(provider);
            String apiKey = apiKeyObj.getEncryptedKey();

            return callLlmApi(provider, config, apiKey, prompt);
        } catch (Exception e) {
            log.error("Lỗi khi thực thi prompt AI cho task '{}': {}. Chuyển sang phản hồi fallback.", taskCode, e.getMessage());
            return fallbackResponse(prompt);
        }
    }

    private String callLlmApi(Provider provider, TaskConfig config, String apiKey, String prompt) throws Exception {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://api.openai.com/v1";

        ProviderProtocol protocol = provider.getProtocol() != null ? provider.getProtocol() : ProviderProtocol.OPENAI_COMPATIBLE;
        String model = config.getModel() != null ? config.getModel() : "gpt-3.5-turbo";

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30));
        String reqBody;

        if (protocol == ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE) {
            String targetUrl = baseUrlStr + "/models/" + model + ":generateContent?key=" + apiKey;
            Map<String, Object> geminiPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );
            reqBody = objectMapper.writeValueAsString(geminiPayload);
            reqBuilder.uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody));
        } else {
            // Default OPENAI_COMPATIBLE
            String targetUrl = baseUrlStr + "/chat/completions";
            Map<String, Object> openAiPayload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Bạn là trợ lý toán học."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", config.getMaxToken() != null ? config.getMaxToken() : 512,
                    "temperature", config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.4
            );
            reqBody = objectMapper.writeValueAsString(openAiPayload);
            reqBuilder.uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody));
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            if (protocol == ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE) {
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            } else {
                return root.path("choices").get(0).path("message").path("content").asText();
            }
        } else {
            log.error("AI Provider HTTP error status {}: {}", status, response.body());
            throw new RuntimeException("AI Provider phản hồi lỗi HTTP " + status);
        }
    }

    private String fallbackResponse(String prompt) {
        return "Hãy phân tích kỹ các giả thiết của bài toán, biến số đã cho và áp dụng công thức tương ứng để thực hiện bước tiếp theo.";
    }
}
