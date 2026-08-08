package com.codegym.mathclass.aiconfig.credit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCostItem {
    private String task;
    private Integer costPerCall;
    private Integer tokensPerCredit;
}
