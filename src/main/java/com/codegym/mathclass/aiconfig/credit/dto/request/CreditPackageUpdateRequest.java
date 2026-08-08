package com.codegym.mathclass.aiconfig.credit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditPackageUpdateRequest {

    @NotBlank(message = "Tên gói không được để trống")
    private String name;

    @NotNull(message = "Số credit không được để trống")
    @Min(value = 1, message = "Số credit tối thiểu là 1")
    private Integer credits;

    @NotNull(message = "Giá không được để trống")
    @Min(value = 1, message = "Giá tối thiểu là 1")
    private Integer price;

    private Boolean enabled;

    private Integer sortOrder;
}
