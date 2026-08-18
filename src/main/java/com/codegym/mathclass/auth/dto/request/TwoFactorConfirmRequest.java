package com.codegym.mathclass.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorConfirmRequest {
    @NotBlank(message = "Mã xác thực không được để trống")
    @Pattern(regexp = "\\d{6}", message = "Mã xác thực phải bao gồm đúng 6 chữ số")
    private String code;
}
