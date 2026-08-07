package com.codegym.mathclass.aiconfig.strategy.impl;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
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
    public String executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt) throws Exception {
        String baseUrlStr = provider.getBaseUrl() != null && !provider.getBaseUrl().trim().isEmpty()
                ? provider.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://api.openai.com/v1";

        String model = config.getModel() != null ? config.getModel() : "gpt-3.5-turbo";
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

        String reqBody = objectMapper.writeValueAsString(openAiPayload);
        
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(30))
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
            return root.path("choices").get(0).path("message").path("content").asText();
        } else {
            log.error("OpenAI Compatible Provider HTTP error status {}: {}", status, response.body());
            throw new RuntimeException("AI Provider phản hồi lỗi HTTP " + status);
        }
    }
}
