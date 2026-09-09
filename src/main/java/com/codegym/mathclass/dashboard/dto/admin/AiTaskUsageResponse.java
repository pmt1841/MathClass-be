package com.codegym.mathclass.dashboard.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTaskUsageResponse {
    private String taskCode;
    private String taskName;
    private long callCount;
    private long successCount;
    private long failedCount;
    private double successRate;
    private double percentage;
}
