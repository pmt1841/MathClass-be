package com.codegym.mathclass.storage.dto;

import jakarta.validation.constraints.Max;
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
public class UpdateStorageCleanupConfigRequest {

    @NotNull(message = "Trạng thái kích hoạt không được để trống")
    private Boolean enabled;

    @NotBlank(message = "Biểu thức cron không được để trống")
    private String cronExpression;

    @NotNull(message = "Thời gian đệm an toàn không được để trống")
    @Min(value = 1, message = "Thời gian đệm an toàn tối thiểu là 1 giờ")
    @Max(value = 168, message = "Thời gian đệm an toàn tối đa là 168 giờ (7 ngày)")
    private Integer gracePeriodHours;
}
