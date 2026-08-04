package com.codegym.mathclass.aiconfig.dto.request;

import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApiKeyStatusPatchRequest {

    @NotNull(message = "Trạng thái status không được để trống")
    private ApiKeyStatus status;
}
