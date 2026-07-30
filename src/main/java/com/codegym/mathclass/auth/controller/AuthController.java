package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Authentication", description = "APIs đăng nhập, đăng ký, xác thực Google và khôi phục mật khẩu")
@RestController
@ApiVersion(1)
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "Đăng nhập bằng Email và Password", description = "Xác thực người dùng và trả về JWT Token")
    @PostMapping("/login")
    public ResponseEntity<UserInfoResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        return ResponseEntity.ok(authService.authenticateUser(loginRequest, response));
    }

    @Operation(summary = "Đăng nhập / Đăng ký qua Google OAuth2", description = "Xác thực người dùng bằng Google ID Token")
    @PostMapping("/google")
    public ResponseEntity<UserInfoResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.authenticateWithGoogle(request, response));
    }

    @Operation(summary = "Đăng xuất", description = "Xóa Cookie JWT và phiên làm việc của người dùng")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.logoutUser(request, response));
    }

    @Operation(summary = "Làm mới JWT Token (Refresh Token)", description = "Cấp lại Access Token mới dựa trên Refresh Cookie")
    @PostMapping("/refresh-token")
    public ResponseEntity<MessageResponse> refreshtoken(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.refreshToken(request, response));
    }

    @Operation(summary = "Đăng ký tài khoản mới", description = "Đăng ký tài khoản người dùng mới và gửi email xác minh")
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody SignupRequest signUpRequest) {
        return ResponseEntity.ok(authService.registerUser(signUpRequest));
    }

    @Operation(summary = "Xác minh tài khoản qua Email", description = "Kích hoạt tài khoản bằng mã Token gửi qua email")
    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verifyUser(@RequestParam("token") String token) {
        return ResponseEntity.ok(authService.verifyUser(token));
    }

    @Operation(summary = "Yêu cầu khôi phục mật khẩu (Quên mật khẩu)", description = "Gửi email chứa liên kết đặt lại mật khẩu")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @Operation(summary = "Đặt lại mật khẩu mới", description = "Cập nhật mật khẩu mới bằng Token khôi phục")
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}
