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
public class TaskCreditConfigUpdateRequest {

    @NotNull(message = "costPerCall không được để trống")
    @Min(value = 0, message = "costPerCall tối thiểu là 0")
    private Integer costPerCall;

    /** Số token đầu ra = 1 credit. NULL giữ nguyên; 0 => tắt tính theo token. */
    @Min(value = 0, message = "tokensPerCredit tối thiểu là 0")
    private Integer tokensPerCredit;

    @NotNull(message = "enabled không được để trống")
    private Boolean enabled;
}
