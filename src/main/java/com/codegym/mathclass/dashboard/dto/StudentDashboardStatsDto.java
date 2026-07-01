package com.codegym.mathclass.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardStatsDto {
    private int joinedClasses;
    private int pendingTasks;
    private int completedTasks;
}
