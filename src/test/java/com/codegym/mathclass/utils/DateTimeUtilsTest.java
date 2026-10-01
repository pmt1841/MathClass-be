package com.codegym.mathclass.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeUtilsTest {

    @Test
    @DisplayName("Should return null when input is null")
    void convertVietnamLocalToUtc_NullInput_ReturnsNull() {
        assertThat(DateTimeUtils.convertVietnamLocalToUtc(null)).isNull();
    }

    @Test
    @DisplayName("Should subtract 7 hours when input is valid within the same day")
    void convertVietnamLocalToUtc_ValidTimeSameDay_SubtractsSevenHours() {
        LocalDateTime localTime = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        LocalDateTime expectedUtc = LocalDateTime.of(2026, 10, 1, 16, 59, 59);

        assertThat(DateTimeUtils.convertVietnamLocalToUtc(localTime)).isEqualTo(expectedUtc);
    }

    @Test
    @DisplayName("Should roll back to previous day when subtracting 7 hours across midnight")
    void convertVietnamLocalToUtc_TimeAcrossMidnight_RollsBackDay() {
        LocalDateTime localTime = LocalDateTime.of(2026, 10, 1, 2, 30, 0);
        LocalDateTime expectedUtc = LocalDateTime.of(2026, 9, 30, 19, 30, 0);

        assertThat(DateTimeUtils.convertVietnamLocalToUtc(localTime)).isEqualTo(expectedUtc);
    }
}
