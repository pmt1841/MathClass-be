package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;

import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
public class LocalPasswordAuthStrategy implements AuthStrategy<LoginRequest> {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final UserTwoFactorAuthRepository userTwoFactorAuthRepository;
    private final com.codegym.mathclass.chat.service.UserPresenceRegistry userPresenceRegistry;

    @Override
    public boolean supports(AuthType authType) {
        return authType == AuthType.LOCAL;
    }

    @Override
    public UserInfoResponse authenticate(LoginRequest loginRequest, HttpServletResponse response) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
        if (userOptional.isEmpty()) {
            throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
        }

        User user = userOptional.get();
        if (loginRequest.getRole() != null && !loginRequest.getRole().isEmpty()) {
            try {
                Role requestedRole = Role.valueOf(loginRequest.getRole().toUpperCase());
                if (requestedRole == Role.TEACHER && user.getRole() == Role.STUDENT) {
                    throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
                }
                if (requestedRole == Role.STUDENT && user.getRole() != Role.STUDENT) {
                    throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
                }
            } catch (IllegalArgumentException ignored) {
            }
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
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            throw new BadRequestException("Email hoặc mật khẩu không đúng. Vui lòng thử lại.");
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đăng nhập: Tài khoản của bạn có thể đã bị khóa hoặc chưa kích hoạt.");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        if (user.getRole() == Role.ADMIN) {
            Optional<UserTwoFactorAuth> auth2faOpt = userTwoFactorAuthRepository.findByUserId(user.getId());
            boolean is2faEnabled = auth2faOpt.isPresent() && auth2faOpt.get().isEnabled();
            String preAuthToken = jwtUtils.generatePreAuthToken(user.getEmail(), user.getId(), Role.ADMIN.name());

            return UserInfoResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .userRole(Role.ADMIN.name())
                    .avatarUrl(user.getAvatarUrl())
                    .is2faRequired(true)
                    .isSetupRequired(!is2faEnabled)
                    .preAuthToken(preAuthToken)
                    .message(is2faEnabled
                            ? "Vui lòng nhập mã xác thực từ Google Authenticator."
                            : "Tài khoản Quản trị viên bắt buộc thiết lập xác thực 2 bước.")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        user.setLastActiveAt(now);
        userRepository.save(user);
        userPresenceRegistry.broadcastPresence(user.getId(), true, now);

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails, loginRequest.isRememberMe());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken.getToken(),
                loginRequest.isRememberMe());

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString());

        String jwtToken = jwtUtils.generateJwtToken(authentication);
        return userMapper.toUserInfoResponse(userDetails, jwtToken);
    }
}
