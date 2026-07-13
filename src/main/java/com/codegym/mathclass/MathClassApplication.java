package com.codegym.mathclass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.codegym.mathclass.config.DotenvInitializer;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MathClassApplication {

    @PostConstruct
    public void init() {
        // Cố định múi giờ mặc định của toàn bộ JVM về UTC để đồng bộ hóa 
        // thời gian chính xác xuống Database và Frontend
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MathClassApplication.class);

        application.addInitializers(new DotenvInitializer());

        application.run(args);
    }

}
