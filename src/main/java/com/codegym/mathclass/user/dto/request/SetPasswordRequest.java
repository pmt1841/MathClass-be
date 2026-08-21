package com.codegym.mathclass.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetPasswordRequest {

    @NotBlank(message = "Mã OTP không được để trống")
    @Size(min = 6, max = 6, message = "Mã OTP phải gồm đúng 6 chữ số")
    private String otpCode;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới phải có tối thiểu 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
    private String confirmPassword;
}
