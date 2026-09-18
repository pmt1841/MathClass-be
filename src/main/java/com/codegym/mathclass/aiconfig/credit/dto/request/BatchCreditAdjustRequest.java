package com.codegym.mathclass.aiconfig.credit.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreditAdjustRequest {

    @NotEmpty(message = "Danh sách userIds không được để trống")
    private List<Long> userIds;

    @NotNull(message = "amount không được để trống")
    private Integer amount;

    private String reason;
}
