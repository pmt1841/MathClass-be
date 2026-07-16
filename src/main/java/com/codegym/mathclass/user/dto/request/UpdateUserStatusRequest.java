package com.codegym.mathclass.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private Boolean isActive;
}
