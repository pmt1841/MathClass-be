package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.TwoFactorLockoutService;
import com.codegym.mathclass.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorLockoutServiceImpl implements TwoFactorLockoutService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES = 15;

    private final UserTwoFactorAuthRepository userTwoFactorAuthRepository;

    @Override
    public void validateNotLocked(UserTwoFactorAuth auth2fa) {
        if (auth2fa.getLockedUntil() != null && auth2fa.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Bạn đã nhập sai mã xác thực quá " + MAX_FAILED_ATTEMPTS
                    + " lần liên tiếp. Vui lòng thử lại sau " + LOCKOUT_MINUTES + " phút.");
        }
    }

    @Override
    @Transactional
    public void recordFailedAttempt(UserTwoFactorAuth auth2fa) {
        int attempts = auth2fa.getFailedAttempts() + 1;
        auth2fa.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            auth2fa.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Tài khoản userId [{}] bị khóa 2FA trong {} phút do nhập sai quá {} lần.",
                    auth2fa.getUserId(), LOCKOUT_MINUTES, MAX_FAILED_ATTEMPTS);
        }
        userTwoFactorAuthRepository.save(auth2fa);
    }

    @Override
    @Transactional
    public void resetLockout(UserTwoFactorAuth auth2fa) {
        auth2fa.setFailedAttempts(0);
        auth2fa.setLockedUntil(null);
        userTwoFactorAuthRepository.save(auth2fa);
    }
}
