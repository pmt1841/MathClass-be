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
        // Đọc file .env, tự động bỏ qua nếu không thấy file (để khi lên Production dùng
        // biến môi trường thật không bị lỗi)
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        Map<String, Object> envProps = new HashMap<>();
        dotenv.entries().forEach(entry -> envProps.put(entry.getKey(), entry.getValue()));

        // Bơm toàn bộ các biến từ file .env vào Hệ thống quản lý thuộc tính của
        // SpringBoot
        applicationContext.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("dotenvProperties", envProps));
    }
}
