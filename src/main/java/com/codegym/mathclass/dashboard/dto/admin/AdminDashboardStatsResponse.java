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
    private UserStatsResponse userStats;
    private ClassroomStatsResponse classroomStats;
    private RevenueStatsResponse revenueStats;
    private BugReportStatsResponse bugReportStats;
    private List<AiTaskUsageResponse> aiTaskUsages;
    private List<PackageSalesResponse> packageSales;
    private List<RecentTransactionResponse> recentTransactions;
    private List<MonthlyUserTrendResponse> userTrends;
    private List<MonthlyRevenueTrendResponse> revenueTrends;
    private List<RecentSystemLogResponse> recentSystemLogs;
    private List<RecentBugReportResponse> recentBugReports;
}
