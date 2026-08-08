package com.codegym.mathclass.aiconfig.credit.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAdjustRequest {

    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotNull(message = "amount không được để trống")
    private Integer amount;

    private String reason;
}
