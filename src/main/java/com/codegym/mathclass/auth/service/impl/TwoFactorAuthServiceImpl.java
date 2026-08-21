package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.request.TwoFactorConfirmRequest;
import com.codegym.mathclass.auth.dto.request.TwoFactorVerifyRequest;
import com.codegym.mathclass.auth.dto.response.TwoFactorConfirmResponse;
import com.codegym.mathclass.auth.dto.response.TwoFactorSetupResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.entity.UserBackupCode;
import com.codegym.mathclass.auth.entity.UserTwoFactorAuth;
import com.codegym.mathclass.auth.repository.UserBackupCodeRepository;
import com.codegym.mathclass.auth.repository.UserTwoFactorAuthRepository;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.auth.service.TotpService;
import com.codegym.mathclass.auth.service.TwoFactorAuthService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.exception.TooManyRequestsException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.security.services.CustomUserDetailsService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorAuthServiceImpl implements TwoFactorAuthService {

    private final TotpService totpService;
    private final UserTwoFactorAuthRepository userTwoFactorAuthRepository;
    private final UserBackupCodeRepository userBackupCodeRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final CustomUserDetailsService customUserDetailsService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Override
    @Transactional
    public TwoFactorSetupResponse initiateSetup(String authHeader) {
        String preAuthToken = extractToken(authHeader);
        validatePreAuthToken(preAuthToken);

        Long userId = jwtUtils.getUserIdFromPreAuthToken(preAuthToken);
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng."));

        UserTwoFactorAuth auth2fa = userTwoFactorAuthRepository.findByUserId(userId)
                .orElseGet(() -> UserTwoFactorAuth.builder()
                        .userId(userId)
                        .isEnabled(false)
                        .build());

        String tempSecret = totpService.generateSecretKey();
        auth2fa.setTempSecretKey(tempSecret);
        userTwoFactorAuthRepository.save(auth2fa);

        String qrCodeDataUrl = totpService.generateQrCodeDataUrl(user.getEmail(), tempSecret);
        String manualEntryKey = tempSecret.replaceAll("(.{4})", "$1 ").trim();

        return TwoFactorSetupResponse.builder()
                .secretKey(tempSecret)
                .qrCodeDataUrl(qrCodeDataUrl)
                .manualEntryKey(manualEntryKey)
                .build();
    }

    @Override
    @Transactional
    public TwoFactorConfirmResponse confirmSetup(TwoFactorConfirmRequest request, String authHeader, HttpServletResponse response) {
        String preAuthToken = extractToken(authHeader);
        validatePreAuthToken(preAuthToken);

        Long userId = jwtUtils.getUserIdFromPreAuthToken(preAuthToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng."));

        UserTwoFactorAuth auth2fa = userTwoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Chưa khởi tạo thiết lập xác thực 2 bước. Vui lòng thử lại."));

        if (auth2fa.getTempSecretKey() == null || auth2fa.getTempSecretKey().trim().isEmpty()) {
            throw new BadRequestException("Chưa khởi tạo khóa bí mật tạm thời. Vui lòng tạo lại mã QR.");
        }

        int codeInt;
        try {
            codeInt = Integer.parseInt(request.getCode().trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Mã xác thực phải bao gồm 6 chữ số.");
        }

        boolean isValid = totpService.verifyCode(auth2fa.getTempSecretKey(), codeInt);
        if (!isValid) {
            throw new BadRequestException("Mã xác thực 6 số không chính xác hoặc đã hết hạn. Vui lòng thử lại.");
        }

        // Kích hoạt 2FA thành công
        auth2fa.setSecretKey(auth2fa.getTempSecretKey());
        auth2fa.setTempSecretKey(null);
        auth2fa.setEnabled(true);
        auth2fa.setEnabledAt(LocalDateTime.now());
        auth2fa.setFailedAttempts(0);
        auth2fa.setLockedUntil(null);
        userTwoFactorAuthRepository.save(auth2fa);

        // Sinh danh sách 8 mã dự phòng (Backup Codes)
        userBackupCodeRepository.deleteByUserId(userId);
        List<String> plainBackupCodes = totpService.generateBackupCodes(8);
        List<UserBackupCode> backupEntities = new ArrayList<>();
        for (String plainCode : plainBackupCodes) {
            backupEntities.add(UserBackupCode.builder()
                    .userId(userId)
                    .codeHash(passwordEncoder.encode(plainCode))
                    .isUsed(false)
                    .build());
        }
        userBackupCodeRepository.saveAll(backupEntities);

        // Cấp phiên đăng nhập hoàn chỉnh
        UserInfoResponse userInfo = issueAuthSession(user, true, response);

        return TwoFactorConfirmResponse.builder()
                .userInfo(userInfo)
                .backupCodes(plainBackupCodes)
                .message("Kích hoạt xác thực 2 bước thành công!")
                .build();
    }

    @Override
    @Transactional
    public UserInfoResponse verifyLogin(TwoFactorVerifyRequest request, String authHeader, HttpServletResponse response) {
        String preAuthToken = extractToken(authHeader);
        validatePreAuthToken(preAuthToken);

        Long userId = jwtUtils.getUserIdFromPreAuthToken(preAuthToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng."));

        UserTwoFactorAuth auth2fa = userTwoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Tài khoản chưa thiết lập xác thực 2 bước."));

        if (!auth2fa.isEnabled() || auth2fa.getSecretKey() == null) {
            throw new BadRequestException("Tài khoản chưa kích hoạt xác thực 2 bước.");
        }

        // Kiểm tra khóa thử sai (Lockout)
        if (auth2fa.getLockedUntil() != null && auth2fa.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("Bạn đã nhập sai mã xác thực quá " + MAX_FAILED_ATTEMPTS + " lần liên tiếp. Vui lòng thử lại sau " + LOCKOUT_MINUTES + " phút.");
        }

        if (!request.isBackupCode()) {
            // Xác thực mã OTP 6 số từ ứng dụng Google Authenticator
            int codeInt;
            try {
                codeInt = Integer.parseInt(request.getCode().trim());
            } catch (NumberFormatException e) {
                handleFailedAttempt(auth2fa);
                throw new BadRequestException("Mã xác thực phải bao gồm 6 chữ số.");
            }

            boolean isValid = totpService.verifyCode(auth2fa.getSecretKey(), codeInt);
            if (!isValid) {
                handleFailedAttempt(auth2fa);
                throw new BadRequestException("Mã xác thực 6 số không đúng hoặc đã hết hạn.");
            }
        } else {
            // Xác thực bằng Mã dự phòng (Backup Code)
            String inputCode = request.getCode().trim();
            List<UserBackupCode> unusedCodes = userBackupCodeRepository.findByUserIdAndIsUsedFalse(userId);

            Optional<UserBackupCode> matchedBackupCode = unusedCodes.stream()
                    .filter(bc -> passwordEncoder.matches(inputCode, bc.getCodeHash()))
                    .findFirst();

            if (!matchedBackupCode.isPresent()) {
                handleFailedAttempt(auth2fa);
                throw new BadRequestException("Mã dự phòng không hợp lệ hoặc đã được sử dụng.");
            }

            // Đánh dấu mã đã sử dụng
            UserBackupCode usedCode = matchedBackupCode.get();
            usedCode.setUsed(true);
            usedCode.setUsedAt(LocalDateTime.now());
            userBackupCodeRepository.save(usedCode);
        }

        // Đăng nhập thành công -> Reset số lần sai
        auth2fa.setFailedAttempts(0);
        auth2fa.setLockedUntil(null);
        userTwoFactorAuthRepository.save(auth2fa);

        return issueAuthSession(user, request.isRememberMe(), response);
    }

    private void handleFailedAttempt(UserTwoFactorAuth auth2fa) {
        int attempts = auth2fa.getFailedAttempts() + 1;
        auth2fa.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            auth2fa.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Tài khoản userId [{}] bị khóa 2FA trong {} phút do nhập sai quá {} lần.",
                    auth2fa.getUserId(), LOCKOUT_MINUTES, MAX_FAILED_ATTEMPTS);
        }
        userTwoFactorAuthRepository.save(auth2fa);
    }

    private UserInfoResponse issueAuthSession(User user, boolean rememberMe, HttpServletResponse response) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());
        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(customUserDetails, rememberMe);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken.getToken(), rememberMe);

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString());

        String jwtToken = jwtUtils.generateJwtToken(customUserDetails.getUsername(), user.getRole().name());
        return userMapper.toUserInfoResponse(customUserDetails, jwtToken);
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        throw new BadRequestException("Thiếu token xác thực Pre-Auth (Authorization header).");
    }

    private void validatePreAuthToken(String token) {
        if (!jwtUtils.validatePreAuthToken(token)) {
            throw new BadRequestException("Phiên xác thực không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
        }
    }
}
