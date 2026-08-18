package com.codegym.mathclass.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorVerifyRequest {
    @NotBlank(message = "Mã xác thực hoặc mã dự phòng không được để trống")
    private String code;

    @JsonProperty("isBackupCode")
    @Builder.Default
    private boolean isBackupCode = false;

    @JsonProperty("rememberMe")
    @Builder.Default
    private boolean rememberMe = false;
}
