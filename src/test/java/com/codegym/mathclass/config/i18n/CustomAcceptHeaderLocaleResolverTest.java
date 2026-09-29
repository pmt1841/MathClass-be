package com.codegym.mathclass.config.i18n;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CustomAcceptHeaderLocaleResolverTest {

    private I18nProperties properties;
    private I18nConfig.CustomAcceptHeaderLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new I18nProperties();
        properties.setDefaultLocale("vi");
        properties.setSupportedLocales(List.of("vi", "en"));
        resolver = new I18nConfig.CustomAcceptHeaderLocaleResolver(properties);
    }

    @Test
    @DisplayName("Header rỗng hoặc null -> Fallback về default locale (vi)")
    void resolveLocale_whenHeaderNullOrEmpty_shouldReturnDefaultLocale() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(Locale.of("vi"), resolver.resolveLocale(request));

        request.addHeader("Accept-Language", "   ");
        assertEquals(Locale.of("vi"), resolver.resolveLocale(request));
    }

    @Test
    @DisplayName("Header chứa ngôn ngữ được hỗ trợ (en, vi) -> Trả về đúng locale")
    void resolveLocale_whenSupportedLanguage_shouldReturnMatchingLocale() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");
        assertEquals(Locale.of("en"), resolver.resolveLocale(request));

        MockHttpServletRequest viRequest = new MockHttpServletRequest();
        viRequest.addHeader("Accept-Language", "vi-VN,vi;q=0.9");
        assertEquals(Locale.of("vi"), resolver.resolveLocale(viRequest));
    }

    @Test
    @DisplayName("Header chứa q-factor (fr;q=0.9, en;q=0.8) -> Bỏ qua ngôn ngữ không hỗ trợ và chọn ngôn ngữ ưu tiên tiếp theo (en)")
    void resolveLocale_withWeightedQFactor_shouldSelectHighestSupported() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr;q=0.9, en;q=0.8");
        assertEquals(Locale.of("en"), resolver.resolveLocale(request));
    }

    @Test
    @DisplayName("Header chứa ký tự wildcard (*) -> Trả về default locale (vi)")
    void resolveLocale_withWildcard_shouldReturnDefaultLocale() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "*");
        assertEquals(Locale.of("vi"), resolver.resolveLocale(request));
    }

    @Test
    @DisplayName("Header bị sai cú pháp (malformed) -> Fallback an toàn về default locale (vi) không ném exception")
    void resolveLocale_withMalformedHeader_shouldFallbackSafely() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", ";;;invalid-header=q=xyz");
        assertDoesNotThrow(() -> {
            Locale resolved = resolver.resolveLocale(request);
            assertEquals(Locale.of("vi"), resolved);
        });
    }

    @Test
    @DisplayName("I18nConfig validateConfiguration Fail-Fast -> Ném IllegalStateException nếu default locale không nằm trong supported locales")
    void validateConfiguration_whenDefaultNotInSupported_shouldThrowException() {
        I18nProperties invalidProps = new I18nProperties();
        invalidProps.setDefaultLocale("ja");
        invalidProps.setSupportedLocales(List.of("vi", "en"));

        I18nConfig config = new I18nConfig(invalidProps);
        assertThrows(IllegalStateException.class, config::validateConfiguration);
    }
}
