package com.codegym.mathclass.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 6, message = "Mật khẩu phải tối thiểu 6 ký tự")
    @Size(max = 24, message = "Mật khẩu không quá 24 ký tự")
    private String password;
}
