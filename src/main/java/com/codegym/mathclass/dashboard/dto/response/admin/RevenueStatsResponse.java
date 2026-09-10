package com.codegym.mathclass.dashboard.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueStatsResponse {
    private long monthlyRevenue;
    private double growthPercentage;
    private long successfulOrdersCount;
}
