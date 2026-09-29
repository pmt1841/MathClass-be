package com.codegym.mathclass.config.i18n;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class I18nConfig {

    private final I18nProperties i18nProperties;

    @PostConstruct
    public void validateConfiguration() {
        String defaultLocale = i18nProperties.getDefaultLocale();
        List<String> supportedLocales = i18nProperties.getSupportedLocales();

        if (defaultLocale == null || defaultLocale.isBlank()) {
            throw new IllegalStateException("Default locale (app.i18n.default-locale) must not be empty.");
        }

        if (supportedLocales == null || supportedLocales.isEmpty()) {
            throw new IllegalStateException("Supported locales (app.i18n.supported-locales) must not be empty.");
        }

        if (!supportedLocales.contains(defaultLocale.toLowerCase())) {
            throw new IllegalStateException("Default locale [" + defaultLocale + "] must be included in supported locales " + supportedLocales);
        }

        log.info("i18n configuration validated successfully: default-locale={}, supported-locales={}", defaultLocale, supportedLocales);
    }

    @Bean
    public LocaleResolver localeResolver() {
        CustomAcceptHeaderLocaleResolver resolver = new CustomAcceptHeaderLocaleResolver(i18nProperties);
        resolver.setDefaultLocale(Locale.of(i18nProperties.getDefaultLocale()));
        return resolver;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setDefaultLocale(Locale.of(i18nProperties.getDefaultLocale()));
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    public static class CustomAcceptHeaderLocaleResolver extends AcceptHeaderLocaleResolver {

        private final I18nProperties properties;

        public CustomAcceptHeaderLocaleResolver(I18nProperties properties) {
            this.properties = properties;
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            String header = request.getHeader("Accept-Language");
            if (header == null || header.trim().isEmpty()) {
                return Locale.of(properties.getDefaultLocale());
            }

            try {
                List<String> candidateLocales = parseAcceptLanguageHeader(header);
                for (String lang : candidateLocales) {
                    if ("*".equals(lang)) {
                        return Locale.of(properties.getDefaultLocale());
                    }
                    String isoCode = lang.split("-")[0].toLowerCase();
                    if (properties.getSupportedLocales().contains(isoCode)) {
                        return Locale.of(isoCode);
                    }
                }
            } catch (Exception e) {
                log.warn("Malformed Accept-Language header received: '{}', falling back to default locale.", header);
            }

            return Locale.of(properties.getDefaultLocale());
        }

        private List<String> parseAcceptLanguageHeader(String header) {
            List<String> result = new ArrayList<>();
            String[] parts = header.split(",");
            List<LanguageWeight> weights = new ArrayList<>();

            for (String part : parts) {
                String[] subParts = part.trim().split(";");
                String lang = subParts[0].trim();
                double weight = 1.0;

                if (subParts.length > 1) {
                    for (int i = 1; i < subParts.length; i++) {
                        String subPart = subParts[i].trim();
                        if (subPart.startsWith("q=")) {
                            try {
                                weight = Double.parseDouble(subPart.substring(2).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
                weights.add(new LanguageWeight(lang, weight));
            }

            weights.sort((a, b) -> Double.compare(b.weight, a.weight));
            for (LanguageWeight lw : weights) {
                result.add(lw.language);
            }
            return result;
        }

        private record LanguageWeight(String language, double weight) {}
    }
}
