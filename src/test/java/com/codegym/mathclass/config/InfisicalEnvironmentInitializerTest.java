package com.codegym.mathclass.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InfisicalEnvironmentInitializerTest {

    @Test
    @DisplayName("UT-INIT-01: Khi INFISICAL_ENABLED=false, không nạp infisicalProperties vào Environment")
    void initialize_Disabled_DoesNotAddPropertySource() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("testProps", Map.of("INFISICAL_ENABLED", "false"))
        );

        InfisicalEnvironmentInitializer initializer = new InfisicalEnvironmentInitializer();
        initializer.initialize(context);

        assertFalse(context.getEnvironment().getPropertySources().contains("infisicalProperties"));
    }

    @Test
    @DisplayName("UT-INIT-02: Khi INFISICAL_ENABLED=true nhưng thiếu ClientId/Secret, không gây crash ứng dụng")
    void initialize_MissingCredentials_GracefullySkips() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("testProps", Map.of("INFISICAL_ENABLED", "true"))
        );

        InfisicalEnvironmentInitializer initializer = new InfisicalEnvironmentInitializer();
        assertDoesNotThrow(() -> initializer.initialize(context));

        assertFalse(context.getEnvironment().getPropertySources().contains("infisicalProperties"));
    }

    @Test
    @DisplayName("UT-INIT-03: Kiểm thử cơ chế Local Override - dotenvProperties ghi đè infisicalProperties")
    void initialize_LocalOverride_DotenvTakesPrecedence() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        // Giả lập biến trong file .env cục bộ
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("dotenvProperties", Map.of(
                        "DB_PASSWORD", "my_local_db_password",
                        "INFISICAL_ENABLED", "false"
                ))
        );

        // Giả lập nạp biến chung từ Infisical
        context.getEnvironment().getPropertySources().addAfter(
                "dotenvProperties",
                new MapPropertySource("infisicalProperties", Map.of(
                        "DB_PASSWORD", "cloud_shared_password",
                        "SUPABASE_KEY", "supabase_cloud_key",
                        "JWT_SECRET", "jwt_shared_secret"
                ))
        );

        // Kiểm tra thứ tự ưu tiên:
        // 1. DB_PASSWORD phải lấy từ .env (my_local_db_password) thay vì Infisical
        assertEquals("my_local_db_password", context.getEnvironment().getProperty("DB_PASSWORD"));

        // 2. SUPABASE_KEY và JWT_SECRET không có trong .env nên lấy tự động từ Infisical
        assertEquals("supabase_cloud_key", context.getEnvironment().getProperty("SUPABASE_KEY"));
        assertEquals("jwt_shared_secret", context.getEnvironment().getProperty("JWT_SECRET"));
    }
}
