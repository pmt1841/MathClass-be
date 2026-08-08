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
public class CreditPurchaseRequest {

    @NotNull(message = "Package ID không được để trống")
    private Long packageId;
}
