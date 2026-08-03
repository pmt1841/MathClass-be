package com.codegym.mathclass.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthRequest {
    @NotBlank(message = "Credential là bắt buộc")
    private String credential;

    private String role;

    private boolean rememberMe;
}
