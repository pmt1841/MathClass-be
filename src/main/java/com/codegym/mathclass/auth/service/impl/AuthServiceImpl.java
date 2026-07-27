package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.PermissionCacheService;
import java.util.List;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.springframework.http.*;
import org.springframework.web.client.*;
import org.springframework.transaction.annotation.Transactional;
import com.codegym.mathclass.auth.entity.PasswordResetToken;
import com.codegym.mathclass.auth.repository.PasswordResetTokenRepository;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.exception.TooManyRequestsException;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PermissionCacheService permissionCacheService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final com.codegym.mathclass.user.mapper.UserMapper userMapper;

    private final ConcurrentHashMap<String, LocalDateTime> forgotPasswordRateLimitMap = new ConcurrentHashMap<>();

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    public UserInfoResponse authenticateUser(LoginRequest loginRequest, HttpServletResponse response) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
        if (!userOptional.isPresent()) {
            throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
        }

        User user = userOptional.get();
        if (loginRequest.getRole() != null && !loginRequest.getRole().isEmpty()) {
            try {
                Role requestedRole = Role.valueOf(loginRequest.getRole().toUpperCase());
                // Học sinh không thể đăng nhập trang Giáo viên
                if (requestedRole == Role.TEACHER && user.getRole() == Role.STUDENT) {
                    throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
                }
                // Giáo viên/Admin không nên đăng nhập trang Học sinh
                if (requestedRole == Role.STUDENT && user.getRole() != Role.STUDENT) {
                    throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
                }
            } catch (IllegalArgumentException e) {
                // Bỏ qua nếu role từ frontend gửi lên không hợp lệ
            }
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đăng nhập: Tài khoản của bạn có thể đã bị khóa hoặc chưa kích hoạt.");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken.getToken());

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString());

        String jwtToken = jwtUtils.generateJwtToken(authentication);
        return userMapper.toUserInfoResponse(userDetails, jwtToken);
    }

    @Override
    public MessageResponse logoutUser(HttpServletRequest request, HttpServletResponse response) {
        String refreshCookie = jwtUtils.getJwtRefreshFromCookies(request);
        if (refreshCookie != null && !refreshCookie.isEmpty()) {
            refreshTokenService.findByToken(refreshCookie).ifPresent(token -> {
                refreshTokenService.deleteByUserId(token.getUser().getId());
            });
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
                        List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
                        CustomUserDetails userDetails = CustomUserDetails.build(user, permissions);
                        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

                        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
                        return new MessageResponse("Token is refreshed successfully!");
                    })
                    .orElseThrow(() -> new BadRequestException("Refresh token không hợp lệ hoặc đã bị thu hồi!"));
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
                .isActive(false)
                .verificationCode(token)
                .build();

        userRepository.save(user);

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
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastRequest = forgotPasswordRateLimitMap.get(email);
        if (lastRequest != null && lastRequest.plusSeconds(60).isAfter(now)) {
            throw new TooManyRequestsException("Bạn đã gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút.");
        }
        forgotPasswordRateLimitMap.put(email, now);

        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            String tokenHash = hashToken(rawToken);

            Optional<PasswordResetToken> existingTokenOpt = passwordResetTokenRepository.findByUserAndIsUsedFalse(user);
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
            passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

            Context context = new Context();
            context.setVariable("fullName", user.getFullName());
            context.setVariable("resetLink", resetLink);
            emailService.sendHtmlMailAsync(user.getEmail(), "Yêu cầu khôi phục mật khẩu MathClass", "forgot-password",
                    context);
        }

        return new MessageResponse("Nếu email của bạn hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư.");
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

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Override
    public UserInfoResponse authenticateWithGoogle(GoogleAuthRequest request, HttpServletResponse httpResponse) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(request.getCredential());
            HttpEntity<String> entity = new HttpEntity<>("parameters",
                    headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> payload = response.getBody();

                String email = (String) payload.get("email");
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                Optional<User> userOptional = userRepository.findByEmail(email);
                User user;

                if (userOptional.isPresent()) {
                    user = userOptional.get();

                    if (request.getRole() != null && !request.getRole().isEmpty()) {
                        try {
                            Role requestedRole = Role.valueOf(request.getRole().toUpperCase());
                            if (requestedRole == Role.TEACHER && user.getRole() == Role.STUDENT) {
                                throw new BadRequestException(
                                        "Tài khoản học sinh không thể truy cập hệ thống của giáo viên.");
                            }
                            if (requestedRole == Role.STUDENT && user.getRole() != Role.STUDENT) {
                                throw new BadRequestException(
                                        "Tài khoản giáo viên không thể truy cập hệ thống của học sinh.");
                            }
                        } catch (IllegalArgumentException e) {
                            // ignore
                        }
                    }

                    if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                        user.setAvatarUrl(pictureUrl);
                        userRepository.save(user);
                    }
                } else {
                    Role role = Role.STUDENT; // Default
                    if (request.getRole() != null) {
                        try {
                            role = Role.valueOf(request.getRole().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            // ignore
                        }
                    }

                    SecureRandom random = new SecureRandom();
                    byte[] bytes = new byte[24];
                    random.nextBytes(bytes);
                    String randomPassword = java.util.Base64.getEncoder().encodeToString(bytes);

                    user = User.builder()
                            .email(email)
                            .fullName(name)
                            .avatarUrl(pictureUrl)
                            .isActive(true)
                            .role(role)
                            .provider(Provider.GOOGLE)
                            .password(encoder.encode(randomPassword))
                            .phoneNumber("")
                            .build();

                    userRepository.save(user);

                    NotificationSettings settings = NotificationSettings.builder()
                            .userId(user.getId())
                            .build();
                    notificationSettingsRepository.save(settings);
                }

                List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
                CustomUserDetails userDetails = CustomUserDetails.build(user, permissions);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());
                ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken.getToken());

                httpResponse.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
                httpResponse.addHeader(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString());

                String jwtToken = jwtUtils.generateJwtToken(authentication);
                return userMapper.toUserInfoResponse(userDetails, jwtToken);

            } else {
                throw new BadRequestException("Token xác thực Google không hợp lệ.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BadRequestException("Đăng nhập thất bại. Vui lòng thử lại sau.");
        }
    }
}
