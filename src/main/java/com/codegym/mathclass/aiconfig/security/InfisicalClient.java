package com.codegym.mathclass.aiconfig.security;

import com.codegym.mathclass.config.InfisicalConfigProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InfisicalClient {

    private final InfisicalConfigProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private RestClient getRestClient() {
        if (this.restClient == null) {
            synchronized (this) {
                if (this.restClient == null) {
                    String baseUrl = (properties != null && properties.getHost() != null && !properties.getHost().isBlank())
                            ? properties.getHost().replaceAll("/+$", "")
                            : "https://app.infisical.com";

                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
                    requestFactory.setReadTimeout(Duration.ofSeconds(10));

                    this.restClient = RestClient.builder()
                            .baseUrl(baseUrl)
                            .requestFactory(requestFactory)
                            .build();
                }
            }
        }
        return this.restClient;
    }

    /**
     * Xác thực với Infisical thông qua Universal Auth (Machine Identity).
     *
     * @param clientId     Client ID của Machine Identity
     * @param clientSecret Client Secret của Machine Identity
     * @return Chuỗi Bearer Access Token
     */
    public String login(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Infisical Client ID và Client Secret không được để trống");
        }

        try {
            Map<String, String> body = new HashMap<>();
            body.put("clientId", clientId);
            body.put("clientSecret", clientSecret);

            String responseBody = getRestClient().post()
                    .uri("/api/v1/auth/universal-auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Phản hồi đăng nhập Universal Auth từ Infisical rỗng");
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode tokenNode = rootNode.get("accessToken");
            if (tokenNode == null || tokenNode.asText().isBlank()) {
                throw new IllegalStateException("Không tìm thấy accessToken trong phản hồi từ Infisical");
            }

            log.info("[Infisical] Xác thực Universal Auth thành công.");
            return tokenNode.asText();
        } catch (Exception e) {
            log.error("[Infisical] Lỗi xác thực Universal Auth với máy chủ Infisical: {}", e.getMessage());
            throw new IllegalStateException("Xác thực Universal Auth với Infisical thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy giá trị chuỗi raw của Secret từ Infisical API.
     *
     * @param accessToken  Bearer Access Token từ bước login
     * @param projectId    Project ID (Workspace ID)
     * @param environment  Môi trường (dev, staging, prod)
     * @param secretPath   Đường dẫn thư mục secret (mặc định /)
     * @param secretName   Tên secret cần lấy (ví dụ AI_ENCRYPTION_MASTER_KEY)
     * @return Chuỗi secret value plaintext
     */
    public String getRawSecret(String accessToken, String projectId, String environment, String secretPath, String secretName) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access Token không được để trống khi truy vấn Secret");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("Infisical Project ID không được để trống");
        }

        try {
            String path = (secretPath == null || secretPath.isBlank()) ? "/" : secretPath;
            String env = (environment == null || environment.isBlank()) ? "dev" : environment;

            String responseBody = getRestClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/secrets/raw/{secretName}")
                            .queryParam("workspaceId", projectId)
                            .queryParam("environment", env)
                            .queryParam("secretPath", path)
                            .build(secretName))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Phản hồi lấy secret từ Infisical rỗng");
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);

            // Bóc tách secretValue từ các cấu trúc JSON có thể trả về của Infisical API
            String secretValue = null;
            if (rootNode.has("secret") && rootNode.get("secret").has("secretValue")) {
                secretValue = rootNode.get("secret").get("secretValue").asText();
            } else if (rootNode.has("secretValue")) {
                secretValue = rootNode.get("secretValue").asText();
            } else if (rootNode.isTextual()) {
                secretValue = rootNode.asText();
            }

            if (secretValue == null || secretValue.isBlank()) {
                throw new IllegalStateException("Không tìm thấy giá trị của secret '" + secretName + "' từ Infisical");
            }

            log.info("[Infisical] Nạp Secret Key '{}' thành công (length: {} ký tự).", secretName, secretValue.length());
            return secretValue;
        } catch (Exception e) {
            log.error("[Infisical] Lỗi khi truy vấn Secret '{}' từ Infisical: {}", secretName, e.getMessage());
            throw new IllegalStateException("Không thể lấy Secret '" + secretName + "' từ Infisical: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy toàn bộ danh sách Secret raw từ Infisical API dưới dạng Map<Key, Value>.
     *
     * @param accessToken  Bearer Access Token từ bước login
     * @param projectId    Project ID (Workspace ID)
     * @param environment  Môi trường (dev, staging, prod)
     * @param secretPath   Đường dẫn thư mục secret (mặc định /)
     * @return Map chứa toàn bộ key-value secrets
     */
    public Map<String, String> getAllRawSecrets(String accessToken, String projectId, String environment, String secretPath) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access Token không được để trống khi truy vấn Secrets");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("Infisical Project ID không được để trống");
        }

        try {
            String path = (secretPath == null || secretPath.isBlank()) ? "/" : secretPath;
            String env = (environment == null || environment.isBlank()) ? "dev" : environment;

            String responseBody = getRestClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/secrets/raw")
                            .queryParam("workspaceId", projectId)
                            .queryParam("environment", env)
                            .queryParam("secretPath", path)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Phản hồi danh sách secrets từ Infisical rỗng");
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);
            Map<String, String> secretsMap = new HashMap<>();

            JsonNode secretsArray = rootNode.has("secrets") ? rootNode.get("secrets") : (rootNode.isArray() ? rootNode : null);

            if (secretsArray != null && secretsArray.isArray()) {
                for (JsonNode secretNode : secretsArray) {
                    String key = secretNode.has("secretKey") ? secretNode.get("secretKey").asText()
                            : (secretNode.has("key") ? secretNode.get("key").asText() : null);
                    String value = secretNode.has("secretValue") ? secretNode.get("secretValue").asText()
                            : (secretNode.has("value") ? secretNode.get("value").asText() : null);

                    if (key != null && !key.isBlank() && value != null) {
                        secretsMap.put(key, value);
                    }
                }
            } else if (rootNode.isObject()) {
                rootNode.properties().forEach(entry -> {
                    if (entry.getValue().isTextual()) {
                        secretsMap.put(entry.getKey(), entry.getValue().asText());
                    }
                });
            }

            log.info("[Infisical] Nạp toàn bộ secrets thành công (tổng cộng: {} biến).", secretsMap.size());
            return secretsMap;
        } catch (Exception e) {
            log.error("[Infisical] Lỗi khi truy vấn toàn bộ secrets từ Infisical: {}", e.getMessage());
            throw new IllegalStateException("Không thể lấy danh sách Secrets từ Infisical: " + e.getMessage(), e);
        }
    }

    /**
     * Phương thức tiện ích lấy secret key đơn lẻ dựa trên cấu hình InfisicalConfigProperties.
     *
     * @return Chuỗi Master Key plaintext
     */
    public String fetchMasterKey() {
        log.info("[Infisical] Đang kết nối tới Infisical (host: {}, env: {})...", properties.getHost(), properties.getEnvironment());
        String token = login(properties.getClientId(), properties.getClientSecret());
        return getRawSecret(
                token,
                properties.getProjectId(),
                properties.getEnvironment(),
                properties.getSecretPath(),
                properties.getSecretName()
        );
    }

    /**
     * Phương thức tiện ích lấy toàn bộ secrets dựa trên cấu hình InfisicalConfigProperties.
     *
     * @return Map chứa toàn bộ secrets
     */
    public Map<String, String> fetchAllSecrets() {
        log.info("[Infisical] Đang kết nối tới Infisical nạp toàn bộ cấu hình (host: {}, env: {})...", properties.getHost(), properties.getEnvironment());
        String token = login(properties.getClientId(), properties.getClientSecret());
        return getAllRawSecrets(
                token,
                properties.getProjectId(),
                properties.getEnvironment(),
                properties.getSecretPath()
        );
    }
}
