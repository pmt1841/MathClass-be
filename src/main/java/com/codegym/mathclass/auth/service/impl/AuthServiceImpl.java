package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.auth.audit.AuthAuditLogger;
import com.codegym.mathclass.auth.dto.request.*;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.PasswordResetToken;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.repository.PasswordResetTokenRepository;
import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.auth.strategy.AuthStrategyFactory;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import com.codegym.mathclass.utils.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthStrategyFactory authStrategyFactory;
    private final AuthAuditLogger authAuditLogger;
    private final UserRepository userRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PermissionCacheService permissionCacheService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final AiCreditService aiCreditService;

    private final ConcurrentHashMap<String, LocalDateTime> forgotPasswordRateLimitMap = new ConcurrentHashMap<>();

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    public UserInfoResponse authenticateUser(LoginRequest loginRequest, HttpServletResponse response) {
        try {
            AuthStrategy<LoginRequest> strategy = authStrategyFactory.getStrategy(AuthType.LOCAL);
            UserInfoResponse userInfo = strategy.authenticate(loginRequest, response);
            authAuditLogger.logSuccess(userInfo.getId(), userInfo.getEmail(), AuthType.LOCAL, null);
            return userInfo;
        } catch (Exception e) {
            authAuditLogger.logFailure(loginRequest != null ? loginRequest.getEmail() : null, AuthType.LOCAL, e.getMessage(), null);
            throw e;
        }
    }

    @Override
    public UserInfoResponse authenticateWithGoogle(GoogleAuthRequest request, HttpServletResponse response) {
        try {
            AuthStrategy<GoogleAuthRequest> strategy = authStrategyFactory.getStrategy(AuthType.GOOGLE);
            UserInfoResponse userInfo = strategy.authenticate(request, response);
            authAuditLogger.logSuccess(userInfo.getId(), userInfo.getEmail(), AuthType.GOOGLE, null);
            return userInfo;
        } catch (Exception e) {
            authAuditLogger.logFailure(null, AuthType.GOOGLE, e.getMessage(), null);
            throw e;
        }
    }

    @Override
    public UserInfoResponse authenticateAdmin2Fa(Admin2FaLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
            AuthStrategy<Admin2FaLoginRequest> strategy = authStrategyFactory.getStrategy(AuthType.ADMIN_2FA);
            UserInfoResponse userInfo = strategy.authenticate(request, response);
            authAuditLogger.logSuccess(userInfo.getId(), userInfo.getEmail(), AuthType.ADMIN_2FA, httpRequest);
            return userInfo;
        } catch (Exception e) {
            authAuditLogger.logFailure(request != null ? request.email() : null, AuthType.ADMIN_2FA, e.getMessage(), httpRequest);
            throw e;
        }
    }

    @Override
    public MessageResponse logoutUser(HttpServletRequest request, HttpServletResponse response) {
        String refreshCookie = jwtUtils.getJwtRefreshFromCookies(request);
        if (refreshCookie != null && !refreshCookie.isEmpty()) {
            refreshTokenService.findByToken(refreshCookie).ifPresent(refreshTokenService::deleteToken);
        }

        ResponseCookie cleanJwtCookie = jwtUtils.getCleanJwtCookie();
        ResponseCookie cleanJwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();

        response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtRefreshCookie.toString());

        SecurityContextHolder.getContext().setAuthentication(null);
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

                        List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
                        CustomUserDetails userDetails = CustomUserDetails.build(user, permissions);
                        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

                        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
                        return new MessageResponse("Token is refreshed successfully!");
                    })
                    .orElseThrow(() -> {
                        ResponseCookie cleanJwtCookie = jwtUtils.getCleanJwtCookie();
                        ResponseCookie cleanJwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
                        response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtCookie.toString());
                        response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtRefreshCookie.toString());
                        return new BadRequestException("Refresh token không hợp lệ hoặc đã bị thu hồi!");
                    });
        }

        throw new BadRequestException("Refresh Token bị trống!");
    }

    @Override
    public MessageResponse registerUser(SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new BadRequestException("Lỗi: Email đã tồn tại!");
        }

        Role requestedRole = signUpRequest.getRole();
        if (requestedRole == Role.ADMIN) {
            throw new BadRequestException("Lỗi đăng ký tài khoản");
        }

        String token = UUID.randomUUID().toString();
        User user = User.builder()
                .email(signUpRequest.getEmail())
                .fullName(signUpRequest.getFullName())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .password(encoder.encode(signUpRequest.getPassword()))
                .role(requestedRole != null ? requestedRole : Role.STUDENT)
                .verificationCode(token)
                .build();

        userRepository.save(user);

        aiCreditService.grantDefaultForNewUser(user.getId(), user.getRole());

        NotificationSettings settings = NotificationSettings.builder()
                .userId(user.getId())
                .build();
        notificationSettingsRepository.save(settings);

        String verifyLink = frontendUrl + "/verify?token=" + token;
        Context context = new Context();
        context.setVariable("fullName", user.getFullName());
        context.setVariable("verifyLink", verifyLink);
        emailService.sendHtmlMailAsync(user.getEmail(), "Xác nhận đăng ký tài khoản MathClass", "auth-verify", context);

        return new MessageResponse("Đăng ký tài khoản thành công! Vui lòng kiểm tra email để xác nhận.");
    }

    @Override
    public MessageResponse verifyUser(String token) {
        Optional<User> userOptional = userRepository.findByVerificationCode(token);
        if (userOptional.isEmpty()) {
            throw new BadRequestException("Lỗi: Mã xác nhận không hợp lệ!");
        }

        User user = userOptional.get();
        user.setActive(true);
        user.setVerificationCode(null);
        userRepository.save(user);

        String role = user.getRole() != null ? user.getRole().name() : "";
        String roleName = "";
        switch (role) {
            case "ADMIN":
                roleName = "Quản trị viên";
                break;
            case "TEACHER":
                roleName = "Giáo viên";
                break;
            case "STUDENT":
                roleName = "Học sinh";
                break;
        }

        String loginLink = frontendUrl + "/login";
        Context context = new Context();
        context.setVariable("fullName", user.getFullName());
        context.setVariable("roleName", roleName);
        context.setVariable("email", user.getEmail());
        context.setVariable("loginLink", loginLink);
        emailService.sendHtmlMailAsync(user.getEmail(), "Kích hoạt tài khoản thành công", "auth-welcome", context);

        return new MessageResponse("Tài khoản đã được kích hoạt thành công!");
    }

    private String hashToken(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi thuật toán mã hóa SHA-256", e);
        }
    }

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastRequest = forgotPasswordRateLimitMap.get(email);
        if (lastRequest != null && lastRequest.plusSeconds(60).isAfter(now)) {
            throw new TooManyRequestsException("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút.");
        }
        forgotPasswordRateLimitMap.put(email, now);

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            String tokenHash = hashToken(rawToken);

            Optional<PasswordResetToken> existingTokenOpt = passwordResetTokenRepository.findByUserAndIsUsedFalse(user);
            PasswordResetToken resetToken = getResetToken(user, tokenHash, existingTokenOpt);
            passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

            sendEmailToUser(user, resetLink);
        }

        return new MessageResponse("Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư.");
    }

    private void sendEmailToUser(User user, String resetLink) {
        Context context = new Context();
        context.setVariable("fullName", user.getFullName());
        context.setVariable("resetLink", resetLink);
        emailService.sendHtmlMailAsync(user.getEmail(), "Yêu cầu khôi phục mật khẩu MathClass", "forgot-password",
                context);
    }

    private PasswordResetToken getResetToken(User user, String tokenHash,
            Optional<PasswordResetToken> existingTokenOpt) {
        PasswordResetToken resetToken;
        if (existingTokenOpt.isPresent()) {
            resetToken = existingTokenOpt.get();
            resetToken.setTokenHash(tokenHash);
            resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(15));
        } else {
            resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiryDate(LocalDateTime.now().plusMinutes(15))
                    .isUsed(false)
                    .build();
        }
        return resetToken;
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String rawToken = request.getToken();
        String tokenHash = hashToken(rawToken);

        Optional<PasswordResetToken> resetTokenOptional = passwordResetTokenRepository
                .findByTokenHashAndIsUsedFalse(tokenHash);

        if (resetTokenOptional.isEmpty()) {
            throw new BadRequestException("Token không hợp lệ hoặc đã qua sử dụng.");
        }

        PasswordResetToken resetToken = resetTokenOptional.get();

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Đường dẫn đặt lại mật khẩu đã hết hạn.");
        }

        User user = resetToken.getUser();
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return new MessageResponse(
                "Mật khẩu của bạn đã được cập nhật thành công. Vui lòng đăng nhập bằng mật khẩu mới.",
                user.getRole().name());
    }
}
