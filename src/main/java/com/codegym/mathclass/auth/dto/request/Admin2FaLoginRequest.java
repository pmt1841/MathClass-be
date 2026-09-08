package com.codegym.mathclass.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Admin2FaLoginRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password,

        @NotBlank(message = "Mã OTP 2FA không được để trống")
        @Size(min = 6, max = 6, message = "Mã OTP 2FA phải bao gồm đúng 6 chữ số")
        String otpCode
) {}
