package com.codegym.mathclass.common.validation;

import com.codegym.mathclass.config.i18n.I18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SupportedLocaleValidatorTest {

    private SupportedLocaleValidator validator;

    @BeforeEach
    void setUp() {
        I18nProperties properties = new I18nProperties();
        properties.setDefaultLocale("vi");
        properties.setSupportedLocales(List.of("vi", "en"));
        validator = new SupportedLocaleValidator(properties);
    }

    @Test
    @DisplayName("Giá trị hợp lệ ('vi', 'en', 'EN') -> Trả về true")
    void isValid_withSupportedLocale_shouldReturnTrue() {
        assertTrue(validator.isValid("vi", null));
        assertTrue(validator.isValid("en", null));
        assertTrue(validator.isValid("EN", null));
        assertTrue(validator.isValid("  vi  ", null));
    }

    @Test
    @DisplayName("Giá trị không hợp lệ (null, rỗng, 'fr', 'ja') -> Trả về false")
    void isValid_withUnsupportedOrEmptyLocale_shouldReturnFalse() {
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("", null));
        assertFalse(validator.isValid("   ", null));
        assertFalse(validator.isValid("fr", null));
        assertFalse(validator.isValid("ja", null));
    }
}
