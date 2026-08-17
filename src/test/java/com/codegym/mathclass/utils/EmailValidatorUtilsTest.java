package com.codegym.mathclass.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailValidatorUtilsTest {

    @Test
    @DisplayName("Should return true for valid email domain with real MX records (gmail.com)")
    void hasValidMxRecord_ValidDomain_ReturnsTrue() {
        boolean result = EmailValidatorUtils.hasValidMxRecord("user@gmail.com");
        boolean result2 = EmailValidatorUtils.hasValidMxRecord("aaaaaaasda@gmail.com");
        assertThat(result).isTrue();
        assertThat(result2).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-existent fake email domain")
    void hasValidMxRecord_FakeDomain_ReturnsFalse() {
        boolean result = EmailValidatorUtils.hasValidMxRecord("user@gmailllll-khong-ton-tai-123456.xyz");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false for invalid syntax email")
    void hasValidMxRecord_InvalidSyntax_ReturnsFalse() {
        boolean result1 = EmailValidatorUtils.hasValidMxRecord("invalidemail");
        boolean result2 = EmailValidatorUtils.hasValidMxRecord("user@invalidnoextension");
        assertThat(result1).isFalse();
        assertThat(result2).isFalse();
    }
}
