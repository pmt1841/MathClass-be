package com.codegym.mathclass.aiconfig.credit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreditAdjustResponse {

    private int total;
    private int successCount;
    private int failureCount;
    private List<String> errors;
    private String message;
}
