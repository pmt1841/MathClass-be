package com.codegym.mathclass.aiconfig.security;

import com.codegym.mathclass.config.InfisicalConfigProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class InfisicalClientTest {

    private MockRestServiceServer mockServer;
    private InfisicalClient infisicalClient;
    private InfisicalConfigProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new InfisicalConfigProperties();
        properties.setEnabled(true);
        properties.setHost("https://app.infisical.com");
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setProjectId("test-project-123");
        properties.setEnvironment("dev");
        properties.setSecretPath("/");
        properties.setSecretName("AI_ENCRYPTION_MASTER_KEY");

        objectMapper = new ObjectMapper();

        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://app.infisical.com");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        infisicalClient = new InfisicalClient(properties, objectMapper);
        infisicalClient.setRestClient(restClient);
    }

    @Test
    @DisplayName("UT-BE-01: Universal Auth login thành công và bóc tách accessToken")
    void login_Success() {
        String mockLoginResponse = """
                {
                    "accessToken": "mock-bearer-token-12345",
                    "expiresIn": 7200,
                    "tokenType": "Bearer"
                }
                """;

        mockServer.expect(requestTo("https://app.infisical.com/api/v1/auth/universal-auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(mockLoginResponse, MediaType.APPLICATION_JSON));

        String token = infisicalClient.login("test-client-id", "test-client-secret");

        assertEquals("mock-bearer-token-12345", token);
        mockServer.verify();
    }

    @Test
    @DisplayName("UT-BE-02: Universal Auth login thất bại ném IllegalStateException")
    void login_Unauthorized_ThrowsException() {
        mockServer.expect(requestTo("https://app.infisical.com/api/v1/auth/universal-auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());

        assertThrows(IllegalStateException.class, () ->
                infisicalClient.login("invalid-client-id", "wrong-secret"));
        mockServer.verify();
    }

    @Test
    @DisplayName("UT-BE-03: Lấy secret raw thành công và bóc tách secretValue")
    void getRawSecret_Success() {
        String mockSecretResponse = """
                {
                    "secret": {
                        "secretKey": "AI_ENCRYPTION_MASTER_KEY",
                        "secretValue": "MathClassSecretMasterKeyForAI2026!",
                        "version": 1
                    }
                }
                """;

        mockServer.expect(requestTo("https://app.infisical.com/api/v3/secrets/raw/AI_ENCRYPTION_MASTER_KEY?workspaceId=test-project-123&environment=dev&secretPath=/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-bearer-token-12345"))
                .andRespond(withSuccess(mockSecretResponse, MediaType.APPLICATION_JSON));

        String secret = infisicalClient.getRawSecret(
                "mock-bearer-token-12345",
                "test-project-123",
                "dev",
                "/",
                "AI_ENCRYPTION_MASTER_KEY"
        );

        assertEquals("MathClassSecretMasterKeyForAI2026!", secret);
        mockServer.verify();
    }

    @Test
    @DisplayName("UT-BE-04: Lấy secret không tồn tại hoặc lỗi ném IllegalStateException")
    void getRawSecret_NotFound_ThrowsException() {
        mockServer.expect(requestTo("https://app.infisical.com/api/v3/secrets/raw/NON_EXISTENT_KEY?workspaceId=test-project-123&environment=dev&secretPath=/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThrows(IllegalStateException.class, () ->
                infisicalClient.getRawSecret(
                        "mock-bearer-token-12345",
                        "test-project-123",
                        "dev",
                        "/",
                        "NON_EXISTENT_KEY"
                ));
        mockServer.verify();
    }

    @Test
    @DisplayName("UT-BE-05: fetchMasterKey thực hiện trọn vẹn luồng login và getRawSecret")
    void fetchMasterKey_Success() {
        String mockLoginResponse = """
                {
                    "accessToken": "mock-token-xyz",
                    "expiresIn": 7200
                }
                """;

        String mockSecretResponse = """
                {
                    "secret": {
                        "secretKey": "AI_ENCRYPTION_MASTER_KEY",
                        "secretValue": "32BytesKeyForInfisicalTest2026!"
                    }
                }
                """;

        mockServer.expect(requestTo("https://app.infisical.com/api/v1/auth/universal-auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockLoginResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://app.infisical.com/api/v3/secrets/raw/AI_ENCRYPTION_MASTER_KEY?workspaceId=test-project-123&environment=dev&secretPath=/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-token-xyz"))
                .andRespond(withSuccess(mockSecretResponse, MediaType.APPLICATION_JSON));

        String key = infisicalClient.fetchMasterKey();

        assertEquals("32BytesKeyForInfisicalTest2026!", key);
        mockServer.verify();
    }

    @Test
    @DisplayName("UT-BE-06: Truyền tham số rỗng ném IllegalArgumentException")
    void invalidArguments_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> infisicalClient.login(null, "secret"));
        assertThrows(IllegalArgumentException.class, () -> infisicalClient.login("id", ""));
        assertThrows(IllegalArgumentException.class, () -> infisicalClient.getRawSecret("", "proj", "dev", "/", "key"));
        assertThrows(IllegalArgumentException.class, () -> infisicalClient.getRawSecret("token", null, "dev", "/", "key"));
        assertThrows(IllegalArgumentException.class, () -> infisicalClient.getAllRawSecrets("", "proj", "dev", "/"));
        assertThrows(IllegalArgumentException.class, () -> infisicalClient.getAllRawSecrets("token", null, "dev", "/"));
    }

    @Test
    @DisplayName("UT-BE-07: getAllRawSecrets bóc tách thành công danh sách secrets")
    void getAllRawSecrets_Success() {
        String mockSecretsResponse = """
                {
                    "secrets": [
                        {
                            "secretKey": "SUPABASE_KEY",
                            "secretValue": "anon-key-123"
                        },
                        {
                            "secretKey": "MAIL_PASSWORD",
                            "secretValue": "gmail-app-pass"
                        },
                        {
                            "secretKey": "JWT_SECRET",
                            "secretValue": "jwt-512-bits-secret"
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://app.infisical.com/api/v3/secrets/raw?workspaceId=test-project-123&environment=dev&secretPath=/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-bearer-token-12345"))
                .andRespond(withSuccess(mockSecretsResponse, MediaType.APPLICATION_JSON));

        var secrets = infisicalClient.getAllRawSecrets(
                "mock-bearer-token-12345",
                "test-project-123",
                "dev",
                "/"
        );

        assertNotNull(secrets);
        assertEquals(3, secrets.size());
        assertEquals("anon-key-123", secrets.get("SUPABASE_KEY"));
        assertEquals("gmail-app-pass", secrets.get("MAIL_PASSWORD"));
        assertEquals("jwt-512-bits-secret", secrets.get("JWT_SECRET"));
        mockServer.verify();
    }

    @Test
    @DisplayName("UT-BE-08: fetchAllSecrets thực hiện trọn vẹn luồng login và tải toàn bộ secrets")
    void fetchAllSecrets_Success() {
        String mockLoginResponse = """
                {
                    "accessToken": "token-all-secrets",
                    "expiresIn": 7200
                }
                """;

        String mockSecretsResponse = """
                {
                    "secrets": [
                        {
                            "secretKey": "AI_ENCRYPTION_MASTER_KEY",
                            "secretValue": "master-key-val"
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://app.infisical.com/api/v1/auth/universal-auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockLoginResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://app.infisical.com/api/v3/secrets/raw?workspaceId=test-project-123&environment=dev&secretPath=/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-all-secrets"))
                .andRespond(withSuccess(mockSecretsResponse, MediaType.APPLICATION_JSON));

        var secrets = infisicalClient.fetchAllSecrets();

        assertNotNull(secrets);
        assertEquals(1, secrets.size());
        assertEquals("master-key-val", secrets.get("AI_ENCRYPTION_MASTER_KEY"));
        mockServer.verify();
    }
}
