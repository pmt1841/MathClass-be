package com.codegym.mathclass.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.HashMap;
import java.util.Map;

public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            // Đọc file .env từ thư mục làm việc hiện tại (Working Directory)
            Dotenv dotenv = Dotenv.configure()
                    .directory(System.getProperty("user.dir"))
                    .load();

            Map<String, Object> envProps = new HashMap<>();
            dotenv.entries().forEach(entry -> envProps.put(entry.getKey(), entry.getValue()));

            // Bơm các biến môi trường vào Spring Environment
            applicationContext.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("dotenvProperties", envProps));

        } catch (Exception e) {
            // Giữ lại một dòng cảnh báo ngắn gọn phòng trường hợp file .env bị mất khi chạy
            // môi trường khác
            System.err.println("[Dotenv] Không thể nạp file .env: " + e.getMessage());
        }
    }
}
