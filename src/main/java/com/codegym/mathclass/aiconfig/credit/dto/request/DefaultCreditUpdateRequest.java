package com.codegym.mathclass.aiconfig.credit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultCreditUpdateRequest {

    @NotNull(message = "defaultCredits không được để trống")
    @Min(value = 0, message = "defaultCredits tối thiểu là 0")
    private Integer defaultCredits;
}
