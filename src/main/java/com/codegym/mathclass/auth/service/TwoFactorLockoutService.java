package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;

public interface TwoFactorLockoutService {

    void validateNotLocked(UserTwoFactorAuth auth2fa);

    void recordFailedAttempt(UserTwoFactorAuth auth2fa);

    void resetLockout(UserTwoFactorAuth auth2fa);
}
