package com.codegym.mathclass.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private Boolean isActive;

    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}

