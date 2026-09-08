package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.Admin2FaLoginRequest;
import com.codegym.mathclass.auth.dto.request.AuthType;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Authentication", description = "APIs xác thực dành riêng cho Quản trị viên hệ thống")
@RestController
@ApiVersion(1)
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng nhập Cổng Quản trị viên 2FA", description = "Xác thực Quản trị viên với Email, Password và mã OTP 2FA 6 chữ số")
    @PostMapping("/login-2fa")
    public ResponseEntity<UserInfoResponse> login2Fa(
            @Valid @RequestBody Admin2FaLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ResponseEntity.ok(authService.authenticateAdmin2Fa(request, httpRequest, httpResponse));
    }
}
