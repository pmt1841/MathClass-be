package com.codegym.mathclass.common.validation;

import com.codegym.mathclass.common.annotation.SupportedLocale;
import com.codegym.mathclass.config.i18n.I18nProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupportedLocaleValidator implements ConstraintValidator<SupportedLocale, String> {

    private final I18nProperties i18nProperties;

    /**
     * No-arg constructor required by Jakarta Bean Validation spec for standalone validator factories.
     */
    public SupportedLocaleValidator() {
        this.i18nProperties = null;
    }

    @Autowired
    public SupportedLocaleValidator(I18nProperties i18nProperties) {
        this.i18nProperties = i18nProperties;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        List<String> supported = (i18nProperties != null && i18nProperties.getSupportedLocales() != null)
                ? i18nProperties.getSupportedLocales()
                : List.of("vi", "en");
        return supported.contains(value.trim().toLowerCase());
    }
}
