package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.auth.service.TotpService;
import com.codegym.mathclass.auth.service.TwoFactorLockoutService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminPortalAuthStrategy implements AuthStrategy<Admin2FaLoginRequest> {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final UserTwoFactorAuthRepository userTwoFactorAuthRepository;
    private final TotpService totpService;
    private final TwoFactorLockoutService twoFactorLockoutService;
    private final AuthSessionService authSessionService;

    @Override
    public boolean supports(AuthType authType) {
        return authType == AuthType.ADMIN_2FA;
    }

    @Override
    public UserInfoResponse authenticate(Admin2FaLoginRequest request, HttpServletResponse response) {
        Optional<User> userOptional = userRepository.findByEmail(request.email());
        if (userOptional.isEmpty()) {
            throw new BadRequestException("Tên đăng nhập hoặc mật khẩu không chính xác.");
        }

        User user = userOptional.get();

        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Tài khoản không có quyền truy cập hệ thống Quản trị viên.");
        }

        if (!user.isActive()) {
            String reasonText = user.getLockReason() != null && !user.getLockReason().trim().isEmpty()
                    ? user.getLockReason()
                    : "Vi phạm tiêu chuẩn sử dụng hệ thống.";
            throw new BadRequestException("Tài khoản của bạn đã bị khóa. Lý do: " + reasonText);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            throw new BadRequestException("Tên đăng nhập hoặc mật khẩu không chính xác.");
        }

        Optional<UserTwoFactorAuth> auth2faOpt = userTwoFactorAuthRepository.findByUserId(user.getId());
        if (auth2faOpt.isEmpty() || !auth2faOpt.get().isEnabled() || auth2faOpt.get().getSecretKey() == null) {
            throw new BadRequestException("Tài khoản Quản trị viên chưa thiết lập hoặc chưa kích hoạt xác thực 2 bước (2FA). Vui lòng hoàn tất thiết lập 2FA.");
        }

        UserTwoFactorAuth auth2fa = auth2faOpt.get();

        twoFactorLockoutService.validateNotLocked(auth2fa);

        int codeInt;
        try {
            codeInt = Integer.parseInt(request.otpCode().trim());
        } catch (NumberFormatException e) {
            twoFactorLockoutService.recordFailedAttempt(auth2fa);
            throw new BadRequestException("Mã xác thực 2FA phải bao gồm 6 chữ số.");
        }

        boolean isValid = totpService.verifyCode(auth2fa.getSecretKey(), codeInt);
        if (!isValid) {
            twoFactorLockoutService.recordFailedAttempt(auth2fa);
            throw new BadRequestException("Mã xác thực 2FA không chính xác hoặc đã hết hạn.");
        }

        twoFactorLockoutService.resetLockout(auth2fa);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        return authSessionService.issueAuthSession(user, true, response);
    }
}
