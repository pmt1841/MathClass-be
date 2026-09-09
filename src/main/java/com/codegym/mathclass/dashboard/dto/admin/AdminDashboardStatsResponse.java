package com.codegym.mathclass.dashboard.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardStatsResponse {
    private Integer selectedMonth;
    private Integer selectedYear;
    private UserStatsDto userStats;
    private ClassroomStatsDto classroomStats;
    private RevenueStatsDto revenueStats;
    private BugReportStatsDto bugReportStats;
    private List<AiTaskUsageDto> aiTaskUsages;
    private List<PackageSalesDto> packageSales;
    private List<RecentTransactionDto> recentTransactions;
    private List<MonthlyUserTrendDto> userTrends;
    private List<MonthlyRevenueTrendDto> revenueTrends;
    private List<RecentSystemLogDto> recentSystemLogs;
    private List<RecentBugReportDto> recentBugReports;
}
