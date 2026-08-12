package com.codegym.mathclass.aiconfig.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfigUpdateRequest {

    @NotNull(message = "Provider ID không được để trống")
    private Long providerId;

    @NotBlank(message = "Tên Model không được để trống")
    private String model;

    @NotNull(message = "Temperature không được để trống")
    @DecimalMin(value = "0.0", message = "Temperature tối thiểu là 0.0")
    @DecimalMax(value = "2.0", message = "Temperature tối đa là 2.0")
    private BigDecimal temperature;

    @NotNull(message = "Max token không được để trống")
    @Min(value = 1, message = "Max token phải lớn hơn 0")
    private Integer maxToken;

    @Builder.Default
    private Boolean enabled = false;
}
