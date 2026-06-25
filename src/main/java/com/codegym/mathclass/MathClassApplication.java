package com.codegym.mathclass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import com.codegym.mathclass.config.DotenvInitializer;

@SpringBootApplication
@EnableAsync
public class MathClassApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MathClassApplication.class);

        application.addInitializers(new DotenvInitializer());

        application.run(args);
    }

}
