package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.audit.AuthAuditLogger;
import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.auth.service.PasswordRecoveryService;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.service.UserRegistrationService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.auth.strategy.AuthStrategyFactory;
import com.codegym.mathclass.chat.service.UserPresenceRegistry;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.jwt.TokenBlacklistService;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthStrategyFactory authStrategyFactory;
    private final AuthAuditLogger authAuditLogger;
    private final UserRegistrationService userRegistrationService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final AuthSessionService authSessionService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserPresenceRegistry userPresenceRegistry;
    private final JwtUtils jwtUtils;

    @Override
    public UserInfoResponse authenticateUser(LoginRequest loginRequest, HttpServletRequest httpRequest, HttpServletResponse response) {
        String clientIp = authAuditLogger.extractClientIp(httpRequest);
        String userAgent = authAuditLogger.extractUserAgent(httpRequest);
        try {
            AuthStrategy<LoginRequest> strategy = authStrategyFactory.getStrategy(AuthType.LOCAL);
            UserInfoResponse userInfo = strategy.authenticate(loginRequest, response);
            authAuditLogger.logSuccess(userInfo.getId(), userInfo.getEmail(), AuthType.LOCAL, clientIp, userAgent);
            return userInfo;
        } catch (Exception e) {
            authAuditLogger.logFailure(loginRequest != null ? loginRequest.getEmail() : null, AuthType.LOCAL,
                    e.getMessage(), clientIp, userAgent);
            throw e;
        }
    }

    @Override
    public UserInfoResponse authenticateWithGoogle(GoogleAuthRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String clientIp = authAuditLogger.extractClientIp(httpRequest);
        String userAgent = authAuditLogger.extractUserAgent(httpRequest);
        try {
            AuthStrategy<GoogleAuthRequest> strategy = authStrategyFactory.getStrategy(AuthType.GOOGLE);
            UserInfoResponse userInfo = strategy.authenticate(request, response);
            authAuditLogger.logSuccess(userInfo.getId(), userInfo.getEmail(), AuthType.GOOGLE, clientIp, userAgent);
            return userInfo;
        } catch (Exception e) {
            authAuditLogger.logFailure(null, AuthType.GOOGLE, e.getMessage(), clientIp, userAgent);
            throw e;
        }
    }

    @Override
    public UserInfoResponse authenticateAdmin2Fa(Admin2FaLoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String clientIp = authAuditLogger.extractClientIp(httpRequest);
        String userAgent = authAuditLogger.extractUserAgent(httpRequest);
        try {
            AuthStrategy<Admin2FaLoginRequest> strategy = authStrategyFactory.getStrategy(AuthType.ADMIN_2FA);
            UserInfoResponse userInfo = strategy.authenticate(request, response);
            authAuditLogger.logSuccess(userInfo.getId(), userInfo.getEmail(), AuthType.ADMIN_2FA, clientIp, userAgent);
            return userInfo;
        } catch (Exception e) {
            authAuditLogger.logFailure(request != null ? request.email() : null, AuthType.ADMIN_2FA, e.getMessage(),
                    clientIp, userAgent);
            throw e;
        }
    }

    @Override
    @Transactional
    public MessageResponse logoutUser(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 1. Đưa Access Token vào Redis Blacklist nếu còn hiệu lực
            String jwtToken = jwtUtils.getJwtFromCookies(request);
            if (jwtToken == null || jwtToken.isEmpty()) {
                String headerAuth = request.getHeader("Authorization");
                if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
                    jwtToken = headerAuth.substring(7);
                }
            }
            if (jwtToken != null && jwtUtils.validateJwtToken(jwtToken)) {
                tokenBlacklistService.blacklistToken(jwtToken);
            }

            Long logoutUserId = null;
            String refreshCookie = jwtUtils.getJwtRefreshFromCookies(request);
            if (refreshCookie != null && !refreshCookie.isEmpty()) {
                var tokenOpt = refreshTokenService.findByToken(refreshCookie);
                if (tokenOpt.isPresent()) {
                    var token = tokenOpt.get();
                    if (token.getUser() != null) {
                        logoutUserId = token.getUser().getId();
                    }
                    refreshTokenService.deleteToken(token);
                }
            }
            if (logoutUserId == null && jwtToken != null && jwtUtils.validateJwtToken(jwtToken)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwtToken);
                var userOpt = userRepository.findByEmail(username);
                if (userOpt.isPresent()) {
                    logoutUserId = userOpt.get().getId();
                }
            }
            if (logoutUserId != null) {
                LocalDateTime now = LocalDateTime.now();
                try {
                    userRepository.updateLastActiveAt(logoutUserId, now);
                } catch (Exception ex) {
                    log.warn("Failed to update lastActiveAt on logout: {}", ex.getMessage());
                }
                userPresenceRegistry.markLoggedOut(logoutUserId);
                userPresenceRegistry.broadcastPresence(logoutUserId, false, now);
            }
        } catch (Exception ex) {
            log.error("Error during logout token cleanup: {}", ex.getMessage());
        } finally {
            authSessionService.clearSessionCookies(response);
            SecurityContextHolder.getContext().setAuthentication(null);
        }
        return new MessageResponse("Đăng xuất thành công!");
    }

    @Override
    public MessageResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenStr = jwtUtils.getJwtRefreshFromCookies(request);

        if (refreshTokenStr != null && !refreshTokenStr.isEmpty()) {
            return refreshTokenService.findByToken(refreshTokenStr)
                    .map(refreshTokenService::verifyExpiration)
                    .map(RefreshToken::getUser)
                    .map(user -> {
                        if (!user.isActive()) {
                            throw new BadRequestException(
                                    "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
                        }

                        authSessionService.setRefreshAccessCookie(user, response);
                        return new MessageResponse("Token is refreshed successfully!");
                    })
                    .orElseThrow(() -> {
                        authSessionService.clearSessionCookies(response);
                        return new BadRequestException("Refresh token không hợp lệ hoặc đã bị thu hồi!");
                    });
        }

        throw new BadRequestException("Refresh Token bị trống!");
    }

    @Override
    public MessageResponse registerUser(SignupRequest signUpRequest) {
        return userRegistrationService.registerUser(signUpRequest);
    }

    @Override
    public MessageResponse verifyUser(String token) {
        return userRegistrationService.verifyUser(token);
    }

    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        return passwordRecoveryService.forgotPassword(request);
    }

    @Override
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        return passwordRecoveryService.resetPassword(request);
    }
}
