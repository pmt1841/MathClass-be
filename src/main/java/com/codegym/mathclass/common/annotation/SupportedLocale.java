package com.codegym.mathclass.common.annotation;

import com.codegym.mathclass.common.validation.SupportedLocaleValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SupportedLocaleValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedLocale {
    String message() default "{USER_LANGUAGE_INVALID}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
