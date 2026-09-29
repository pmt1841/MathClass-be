package com.codegym.mathclass.config.i18n;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.i18n")
public class I18nProperties {

    private String defaultLocale = "vi";
    private List<String> supportedLocales = new ArrayList<>(List.of("vi", "en"));
}
