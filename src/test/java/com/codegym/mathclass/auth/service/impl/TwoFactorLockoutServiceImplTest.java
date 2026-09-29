package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorLockoutServiceImplTest {

    @Mock
    private UserTwoFactorAuthRepository userTwoFactorAuthRepository;

    @InjectMocks
    private TwoFactorLockoutServiceImpl lockoutService;

    private UserTwoFactorAuth auth2fa;

    @BeforeEach
    void setUp() {
        auth2fa = UserTwoFactorAuth.builder()
                .userId(1L)
                .isEnabled(true)
                .failedAttempts(0)
                .lockedUntil(null)
                .build();
    }

    @Test
    @DisplayName("validateNotLocked should pass when lockedUntil is null")
    void validateNotLocked_NullLockedUntil_Passes() {
        assertThatCode(() -> lockoutService.validateNotLocked(auth2fa)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNotLocked should pass when lockedUntil is in the past")
    void validateNotLocked_PastLockedUntil_Passes() {
        auth2fa.setLockedUntil(LocalDateTime.now().minusMinutes(5));
        assertThatCode(() -> lockoutService.validateNotLocked(auth2fa)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNotLocked should throw TooManyRequestsException when lockedUntil is in the future")
    void validateNotLocked_FutureLockedUntil_ThrowsException() {
        auth2fa.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        assertThatThrownBy(() -> lockoutService.validateNotLocked(auth2fa))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("quá 5 lần liên tiếp");
    }

    @Test
    @DisplayName("recordFailedAttempt should increment failedAttempts without locking if under max threshold")
    void recordFailedAttempt_UnderThreshold_IncrementsWithoutLocking() {
        auth2fa.setFailedAttempts(2);

        lockoutService.recordFailedAttempt(auth2fa);

        assertThat(auth2fa.getFailedAttempts()).isEqualTo(3);
        assertThat(auth2fa.getLockedUntil()).isNull();
        verify(userTwoFactorAuthRepository).save(auth2fa);
    }

    @Test
    @DisplayName("recordFailedAttempt should set lockedUntil when reaching max threshold (5 attempts)")
    void recordFailedAttempt_ReachingThreshold_LocksAccount() {
        auth2fa.setFailedAttempts(4);

        lockoutService.recordFailedAttempt(auth2fa);

        assertThat(auth2fa.getFailedAttempts()).isEqualTo(5);
        assertThat(auth2fa.getLockedUntil()).isNotNull();
        assertThat(auth2fa.getLockedUntil()).isAfter(LocalDateTime.now());
        verify(userTwoFactorAuthRepository).save(auth2fa);
    }

    @Test
    @DisplayName("resetLockout should reset failedAttempts to 0 and clear lockedUntil")
    void resetLockout_Success() {
        auth2fa.setFailedAttempts(3);
        auth2fa.setLockedUntil(LocalDateTime.now().plusMinutes(5));

        lockoutService.resetLockout(auth2fa);

        assertThat(auth2fa.getFailedAttempts()).isEqualTo(0);
        assertThat(auth2fa.getLockedUntil()).isNull();
        verify(userTwoFactorAuthRepository).save(auth2fa);
    }
}
