package com.codegym.mathclass.config;

import com.codegym.mathclass.aiconfig.security.InfisicalClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * Initializer nạp toàn bộ danh sách biến bí mật dùng chung từ Infisical vào Spring Environment
 * ngay trong giai đoạn khởi tạo ApplicationContext.
 *
 * <p>Cơ chế phân cấp độ ưu tiên (Precedence Hierarchy):
 * 1. {@code dotenvProperties} (File .env cục bộ - Local Overrides) có mức ưu tiên cao nhất.
 * 2. {@code infisicalProperties} (Biến dùng chung từ Infisical) có mức ưu tiên thứ 2.
 * 3. {@code application.properties} (Giá trị mặc định) là fallback cuối cùng.
 */
@Slf4j
public class InfisicalEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        String enabledStr = resolveProperty(environment, "INFISICAL_ENABLED", "mathclass.infisical.enabled");
        if (!"true".equalsIgnoreCase(enabledStr)) {
            log.info("[InfisicalInitializer] Infisical chưa được bật (INFISICAL_ENABLED != true). Bỏ qua nạp biến tự động.");
            return;
        }

        String host = resolveProperty(environment, "INFISICAL_HOST", "mathclass.infisical.host");
        if (host == null || host.isBlank()) {
            host = "https://app.infisical.com";
        }

        String clientId = resolveProperty(environment, "INFISICAL_CLIENT_ID", "mathclass.infisical.client-id");
        String clientSecret = resolveProperty(environment, "INFISICAL_CLIENT_SECRET", "mathclass.infisical.client-secret");
        String projectId = resolveProperty(environment, "INFISICAL_PROJECT_ID", "mathclass.infisical.project-id");
        String env = resolveProperty(environment, "INFISICAL_ENV", "mathclass.infisical.environment");
        if (env == null || env.isBlank()) {
            env = "dev";
        }

        String secretPath = resolveProperty(environment, "INFISICAL_SECRET_PATH", "mathclass.infisical.secret-path");
        if (secretPath == null || secretPath.isBlank()) {
            secretPath = "/";
        }

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank() || projectId == null || projectId.isBlank()) {
            log.warn("[InfisicalInitializer] INFISICAL_ENABLED=true nhưng thiếu ClientId / ClientSecret / ProjectId. Bỏ qua nạp biến tự động.");
            return;
        }

        try {
            InfisicalConfigProperties properties = new InfisicalConfigProperties();
            properties.setEnabled(true);
            properties.setHost(host);
            properties.setClientId(clientId);
            properties.setClientSecret(clientSecret);
            properties.setProjectId(projectId);
            properties.setEnvironment(env);
            properties.setSecretPath(secretPath);

            ObjectMapper objectMapper = new ObjectMapper();
            InfisicalClient client = new InfisicalClient(properties, objectMapper);

            Map<String, String> secrets = client.fetchAllSecrets();

            if (secrets != null && !secrets.isEmpty()) {
                Map<String, Object> propertyMap = new HashMap<>(secrets);
                MapPropertySource infisicalPropertySource = new MapPropertySource("infisicalProperties", propertyMap);

                MutablePropertySources propertySources = environment.getPropertySources();

                // Nếu có dotenvProperties, ta chèn infisicalProperties ngay sau dotenvProperties
                // để đảm bảo file .env cá nhân có thể ghi đè (Local Override) lên Infisical
                if (propertySources.contains("dotenvProperties")) {
                    propertySources.addAfter("dotenvProperties", infisicalPropertySource);
                } else {
                    propertySources.addFirst(infisicalPropertySource);
                }

                log.info("[InfisicalInitializer] Đã nạp thành công {} biến môi trường từ Infisical vào Spring Environment.", secrets.size());
            }
        } catch (Exception e) {
            log.error("[InfisicalInitializer] Lỗi khi nạp biến môi trường tự động từ Infisical: {}", e.getMessage());
            // Không ném exception làm sập app nếu có thể chạy fallback từ local .env
        }
    }

    private String resolveProperty(ConfigurableEnvironment environment, String envKey, String propKey) {
        String value = environment.getProperty(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = environment.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return System.getProperty(envKey);
    }
}
