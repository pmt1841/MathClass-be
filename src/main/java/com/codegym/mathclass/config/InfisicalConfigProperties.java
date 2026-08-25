package com.codegym.mathclass.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mathclass.infisical")
public class InfisicalConfigProperties {

    /**
     * Bật hoặc tắt tính năng lấy secret key từ Infisical (mặc định false).
     */
    private boolean enabled = false;

    /**
     * Địa chỉ máy chủ Infisical (Cloud hoặc Self-hosted).
     */
    private String host = "https://app.infisical.com";

    /**
     * Client ID của Machine Identity (Universal Auth).
     */
    private String clientId;

    /**
     * Client Secret của Machine Identity.
     */
    private String clientSecret;

    /**
     * Project / Workspace ID trên Infisical.
     */
    private String projectId;

    /**
     * Tên môi trường (dev, staging, prod).
     */
    private String environment = "dev";

    /**
     * Đường dẫn thư mục chứa secret trên Infisical.
     */
    private String secretPath = "/";

    /**
     * Tên secret key cần lấy (mặc định AI_ENCRYPTION_MASTER_KEY).
     */
    private String secretName = "AI_ENCRYPTION_MASTER_KEY";
}
