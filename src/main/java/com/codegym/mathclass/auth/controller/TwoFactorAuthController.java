package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.TwoFactorConfirmRequest;
import com.codegym.mathclass.auth.dto.request.TwoFactorVerifyRequest;
import com.codegym.mathclass.auth.dto.response.TwoFactorConfirmResponse;
import com.codegym.mathclass.auth.dto.response.TwoFactorSetupResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.service.TwoFactorAuthService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Two Factor Authentication", description = "APIs thiết lập và xác thực 2 bước (Google Authenticator TOTP & Backup Codes)")
@RestController
@ApiVersion(1)
@RequestMapping("/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    @Operation(summary = "Khởi tạo thiết lập 2FA", description = "Sinh mã QR Code và Khóa bí mật tạm thời cho Admin")
    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> initiateSetup(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        return ResponseEntity.ok(twoFactorAuthService.initiateSetup(authHeader));
    }

    @Operation(summary = "Xác nhận kích hoạt 2FA lần đầu", description = "Xác minh mã 6 số đầu tiên, kích hoạt 2FA, cấp danh sách Mã dự phòng và hoàn tất đăng nhập")
    @PostMapping("/setup/confirm")
    public ResponseEntity<TwoFactorConfirmResponse> confirmSetup(
            @Valid @RequestBody TwoFactorConfirmRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            HttpServletResponse response) {
        return ResponseEntity.ok(twoFactorAuthService.confirmSetup(request, authHeader, response));
    }

    @Operation(summary = "Xác thực đăng nhập 2FA định kỳ", description = "Xác thực mã 6 số TOTP hoặc mã dự phòng (Backup code) khi đăng nhập")
    @PostMapping("/verify")
    public ResponseEntity<UserInfoResponse> verifyLogin(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            HttpServletResponse response) {
        return ResponseEntity.ok(twoFactorAuthService.verifyLogin(request, authHeader, response));
    }
}
