package com.codegym.mathclass.aiconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApiKeyCreateRequest {

    private String name;

    @NotBlank(message = "API Key không được để trống")
    private String apiKey;

    private Integer priority = 0;
}
