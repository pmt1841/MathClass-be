package com.codegym.mathclass.dashboard.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsResponse {
    private long totalUsers;
    private long teacherCount;
    private long studentCount;
    private long newUsersInMonth;
    private long activeUsersToday;
}
