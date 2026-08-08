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
public class CreditBalanceResponse {
    private Long userId;
    private Integer balance;
    private Integer totalEarned;
    private Integer totalSpent;
    private List<CreditCostItem> costs;
}
