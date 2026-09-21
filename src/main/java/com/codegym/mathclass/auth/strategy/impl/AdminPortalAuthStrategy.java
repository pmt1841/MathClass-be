package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.service.TotpService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminPortalAuthStrategy implements AuthStrategy<Admin2FaLoginRequest> {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final UserTwoFactorAuthRepository userTwoFactorAuthRepository;
    private final TotpService totpService;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

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

        if (auth2fa.getLockedUntil() != null && auth2fa.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Bạn đã nhập sai mã xác thực quá " + MAX_FAILED_ATTEMPTS + " lần liên tiếp. Vui lòng thử lại sau " + LOCKOUT_MINUTES + " phút.");
        }

        int codeInt;
        try {
            codeInt = Integer.parseInt(request.otpCode().trim());
        } catch (NumberFormatException e) {
            handleFailedAttempt(auth2fa);
            throw new BadRequestException("Mã xác thực 2FA phải bao gồm 6 chữ số.");
        }

        boolean isValid = totpService.verifyCode(auth2fa.getSecretKey(), codeInt);
        if (!isValid) {
            handleFailedAttempt(auth2fa);
            throw new BadRequestException("Mã xác thực 2FA không chính xác hoặc đã hết hạn.");
        }

        auth2fa.setFailedAttempts(0);
        auth2fa.setLockedUntil(null);
        userTwoFactorAuthRepository.save(auth2fa);


        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails, true);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken.getToken(), true);

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString());

        ResponseCookie cleanLoggedOutCookie = ResponseCookie.from("mathclass_logged_out", "")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cleanLoggedOutCookie.toString());

        String jwtToken = jwtUtils.generateJwtToken(authentication);
        return userMapper.toUserInfoResponse(userDetails, jwtToken);
    }

    private void handleFailedAttempt(UserTwoFactorAuth auth2fa) {
        int attempts = auth2fa.getFailedAttempts() + 1;
        auth2fa.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            auth2fa.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Tài khoản Admin userId [{}] bị khóa 2FA trong {} phút do nhập sai quá {} lần.",
                    auth2fa.getUserId(), LOCKOUT_MINUTES, MAX_FAILED_ATTEMPTS);
        }
        userTwoFactorAuthRepository.save(auth2fa);
    }
}
