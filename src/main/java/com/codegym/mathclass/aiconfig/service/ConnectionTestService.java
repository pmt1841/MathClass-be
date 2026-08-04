package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.TestConnectionRequest;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.repository.ApiKeyRepository;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionTestService {

    private final ApiKeyRepository apiKeyRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TestConnectionResponse testConnection(TestConnectionRequest request) {
        long startTime = System.currentTimeMillis();
        String apiKey = request.getApiKey() != null ? request.getApiKey().trim() : "";
        if (apiKey.isEmpty()) {
            return TestConnectionResponse.builder()
                    .success(false)
                    .valid(false)
                    .errorCode("INVALID_KEY")
                    .message("API Key không được để trống")
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            HttpResponse<String> response = executeHttpRequest(request);
            long latencyMs = System.currentTimeMillis() - startTime;
            int status = response.statusCode();
            ProviderProtocol protocol = request.getProtocol() != null ? request.getProtocol() : ProviderProtocol.OPENAI_COMPATIBLE;

            if (status >= 200 && status < 300) {
                return TestConnectionResponse.builder()
                        .success(true)
                        .valid(true)
                        .latencyMs(latencyMs)
                        .message("Kiểm tra kết nối thành công! Giao thức " + protocol + " hoạt động bình thường.")
                        .build();
            } else if (status == 401 || (status == 400 && response.body().contains("API_KEY_INVALID"))) {
                return TestConnectionResponse.builder()
                        .success(false)
                        .valid(false)
                        .errorCode("INVALID_KEY")
                        .message("API Key không hợp lệ hoặc đã bị hủy (HTTP " + status + ")")
                        .latencyMs(latencyMs)
                        .build();
            } else if (status == 429) {
                return TestConnectionResponse.builder()
                        .success(false)
                        .valid(false)
                        .errorCode("QUOTA_EXHAUSTED")
                        .message("API Key đúng nhưng Tài khoản đã HẾT QUOTA / Credits (HTTP 429)")
                        .latencyMs(latencyMs)
                        .build();
            } else if (status == 403 || status == 404) {
                return TestConnectionResponse.builder()
                        .success(false)
                        .valid(false)
                        .errorCode("AUTH_FAILED")
                        .message("Tài khoản không có quyền truy cập hoặc Endpoint không tồn tại (HTTP " + status + ")")
                        .latencyMs(latencyMs)
                        .build();
            } else {
                return TestConnectionResponse.builder()
                        .success(false)
                        .valid(false)
                        .errorCode("PROVIDER_ERROR")
                        .message("Provider phản hồi lỗi HTTP " + status)
                        .latencyMs(latencyMs)
                        .build();
            }
        } catch (HttpTimeoutException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            return TestConnectionResponse.builder()
                    .success(false)
                    .valid(false)
                    .errorCode("TIMEOUT")
                    .message("Quá thời gian chờ phản hồi từ Provider (Timeout 10s)")
                    .latencyMs(latencyMs)
                    .build();
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("Test connection thất bại: {}", e.getMessage());
            return TestConnectionResponse.builder()
                    .success(false)
                    .valid(false)
                    .errorCode("CONNECTION_FAILED")
                    .message("Không thể kết nối tới Provider: " + e.getMessage())
                    .latencyMs(latencyMs)
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public TestConnectionResponse verifyKey(Long keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API Key không tồn tại với ID: " + keyId));

        Provider provider = apiKey.getProvider();
        String plainKey = apiKey.getEncryptedKey(); // Auto decrypted by ApiKeyCryptoConverter

        TestConnectionRequest testReq = TestConnectionRequest.builder()
                .providerCode(provider.getCode())
                .apiKey(plainKey)
                .baseUrl(provider.getBaseUrl())
                .protocol(provider.getProtocol())
                .authHeaderName(provider.getAuthHeaderName())
                .authHeaderPrefix(provider.getAuthHeaderPrefix())
                .authQueryParam(provider.getAuthQueryParam())
                .healthCheckPath(provider.getHealthCheckPath())
                .build();

        return testConnection(testReq);
    }

    @Transactional(readOnly = true)
    public List<String> fetchAvailableModels(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Provider với ID: " + providerId));

        List<ApiKey> keys = provider.getApiKeys();
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        ApiKey activeKey = keys.stream()
                .filter(k -> k.getStatus() == ApiKeyStatus.ACTIVE)
                .findFirst()
                .orElse(keys.get(0));

        String apiKey = activeKey.getEncryptedKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Collections.emptyList();
        }

        TestConnectionRequest req = TestConnectionRequest.builder()
                .providerCode(provider.getCode())
                .apiKey(apiKey)
                .baseUrl(provider.getBaseUrl())
                .protocol(provider.getProtocol())
                .authHeaderName(provider.getAuthHeaderName())
                .authHeaderPrefix(provider.getAuthHeaderPrefix())
                .authQueryParam(provider.getAuthQueryParam())
                .healthCheckPath(provider.getHealthCheckPath())
                .build();

        try {
            HttpResponse<String> response = executeHttpRequest(req);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            Set<String> modelSet = new HashSet<>();

            if (root.has("data") && root.get("data").isArray()) {
                for (JsonNode node : root.get("data")) {
                    if (node.has("id")) {
                        modelSet.add(node.get("id").asText());
                    }
                }
            } else if (root.has("models") && root.get("models").isArray()) {
                for (JsonNode node : root.get("models")) {
                    if (node.has("name")) {
                        String name = node.get("name").asText();
                        if (name.startsWith("models/")) {
                            name = name.substring("models/".length());
                        }
                        modelSet.add(name);
                    }
                }
            }

            List<String> result = new ArrayList<>(modelSet);
            Collections.sort(result);
            return result;
        } catch (Exception e) {
            log.warn("Không thể tải danh sách models cho Provider ID {}: {}", providerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private HttpResponse<String> executeHttpRequest(TestConnectionRequest request) throws Exception {
        String apiKey = request.getApiKey() != null ? request.getApiKey().trim() : "";

        Optional<Provider> providerOpt = request.getProviderCode() != null
                ? providerRepository.findByCode(request.getProviderCode().toUpperCase().trim())
                : Optional.empty();

        ProviderProtocol protocol = request.getProtocol() != null
                ? request.getProtocol()
                : providerOpt.map(Provider::getProtocol).orElse(ProviderProtocol.OPENAI_COMPATIBLE);

        String baseUrlStr = request.getBaseUrl() != null && !request.getBaseUrl().trim().isEmpty()
                ? request.getBaseUrl().trim().replaceAll("/+$", "")
                : providerOpt.map(Provider::getBaseUrl).orElse("https://api.openai.com/v1").replaceAll("/+$", "");

        String healthPath = request.getHealthCheckPath() != null && !request.getHealthCheckPath().trim().isEmpty()
                ? request.getHealthCheckPath().trim()
                : providerOpt.map(Provider::getHealthCheckPath).orElse(null);

        String authHeaderName = request.getAuthHeaderName() != null && !request.getAuthHeaderName().trim().isEmpty()
                ? request.getAuthHeaderName().trim()
                : providerOpt.map(Provider::getAuthHeaderName).orElse(null);

        String authHeaderPrefix = request.getAuthHeaderPrefix() != null
                ? request.getAuthHeaderPrefix()
                : providerOpt.map(Provider::getAuthHeaderPrefix).orElse("");

        String authQueryParam = request.getAuthQueryParam() != null && !request.getAuthQueryParam().trim().isEmpty()
                ? request.getAuthQueryParam().trim()
                : providerOpt.map(Provider::getAuthQueryParam).orElse(null);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10));
        String targetUrl;

        switch (protocol) {
            case GOOGLE_GEMINI_COMPATIBLE:
                String pathGemini = (healthPath != null && !healthPath.isEmpty()) ? healthPath : "/models";
                if (!pathGemini.startsWith("/")) pathGemini = "/" + pathGemini;
                targetUrl = baseUrlStr + pathGemini + (pathGemini.contains("?") ? "&key=" + apiKey : "?key=" + apiKey);
                reqBuilder.uri(URI.create(targetUrl)).GET();
                break;

            case ANTHROPIC_COMPATIBLE:
                String pathAnthropic = (healthPath != null && !healthPath.isEmpty()) ? healthPath : "/models";
                if (!pathAnthropic.startsWith("/")) pathAnthropic = "/" + pathAnthropic;
                targetUrl = baseUrlStr + pathAnthropic;
                reqBuilder.uri(URI.create(targetUrl))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .GET();
                break;

            case CUSTOM_REST:
                String pathCustom = (healthPath != null && !healthPath.isEmpty()) ? healthPath : "/models";
                if (!pathCustom.startsWith("/")) pathCustom = "/" + pathCustom;

                if (authQueryParam != null && !authQueryParam.isEmpty()) {
                    targetUrl = baseUrlStr + pathCustom + (pathCustom.contains("?") ? "&" : "?") + authQueryParam + "=" + apiKey;
                } else {
                    targetUrl = baseUrlStr + pathCustom;
                }

                if (authHeaderName != null && !authHeaderName.isEmpty()) {
                    String headerVal = (authHeaderPrefix != null ? authHeaderPrefix : "") + apiKey;
                    reqBuilder.header(authHeaderName, headerVal);
                }
                reqBuilder.uri(URI.create(targetUrl)).GET();
                break;

            case OPENAI_COMPATIBLE:
            default:
                String pathOpenAi = (healthPath != null && !healthPath.isEmpty()) ? healthPath : "/models";
                if (!pathOpenAi.startsWith("/")) pathOpenAi = "/" + pathOpenAi;
                targetUrl = baseUrlStr + pathOpenAi;
                reqBuilder.uri(URI.create(targetUrl))
                        .header("Authorization", "Bearer " + apiKey)
                        .GET();
                break;
        }

        return httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
