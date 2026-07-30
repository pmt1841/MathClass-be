package com.codegym.mathclass.config;

import com.codegym.mathclass.common.annotation.ApiVersion;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v1",
                HandlerTypePredicate.forAnnotation(RestController.class)
                        .and(clazz -> !clazz.isAnnotationPresent(ApiVersion.class) || clazz.getAnnotation(ApiVersion.class).value() == 1));

        configurer.addPathPrefix("/api/v2",
                HandlerTypePredicate.forAnnotation(ApiVersion.class)
                        .and(clazz -> clazz.getAnnotation(ApiVersion.class).value() == 2));
    }
}
