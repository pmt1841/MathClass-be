package com.codegym.mathclass.dashboard.service.impl;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditTransactionRepository;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.dashboard.dto.admin.*;
import com.codegym.mathclass.dashboard.service.AdminDashboardService;
import com.codegym.mathclass.systemlog.repository.SystemLogRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final CreditPurchaseOrderRepository creditPurchaseOrderRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final CreditPackageRepository creditPackageRepository;
    private final BugReportRepository bugReportRepository;
    private final SystemLogRepository systemLogRepository;

    @Override
    public AdminDashboardStatsResponse getAdminDashboardStats(Integer month, Integer year) {
        LocalDate now = LocalDate.now();
        int effectiveYear = (year != null && year >= 2000 && year <= 2100) ? year : now.getYear();
        int effectiveMonth = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();

        YearMonth targetMonth = YearMonth.of(effectiveYear, effectiveMonth);
        LocalDateTime startDate = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime startOfLastMonth = targetMonth.minusMonths(1).atDay(1).atStartOfDay();

        boolean isCurrentMonth = (effectiveYear == now.getYear() && effectiveMonth == now.getMonthValue());

        LocalDateTime startOfYear = LocalDate.of(effectiveYear, 1, 1).atStartOfDay();
        LocalDateTime endOfYear = LocalDate.of(effectiveYear + 1, 1, 1).atStartOfDay();

        return AdminDashboardStatsResponse.builder()
                .selectedMonth(effectiveMonth)
                .selectedYear(effectiveYear)
                .userStats(calculateUserStats(startDate, endDate, isCurrentMonth))
                .classroomStats(calculateClassroomStats(endDate))
                .revenueStats(calculateRevenueStats(startDate, endDate, startOfLastMonth))
                .bugReportStats(calculateBugReportStats(endDate))
                .aiTaskUsages(calculateAiTaskUsages(startDate, endDate))
                .packageSales(calculatePackageSales(startDate, endDate))
                .recentTransactions(getTransactions(startDate, endDate))
                .userTrends(calculateUserTrends(startOfYear, endOfYear))
                .revenueTrends(calculateRevenueTrends(startOfYear, endOfYear))
                .recentSystemLogs(getRecentSystemLogs())
                .recentBugReports(getRecentBugReports())
                .build();
    }

    private UserStatsDto calculateUserStats(LocalDateTime startDate, LocalDateTime endDate, boolean isCurrentMonth) {
        long totalUsers = userRepository.countByCreatedAtLessThan(endDate);
        long teacherCount = userRepository.countByRoleAndCreatedAtLessThan(Role.TEACHER, endDate);
        long studentCount = userRepository.countByRoleAndCreatedAtLessThan(Role.STUDENT, endDate);

        // Số tài khoản mới trong tháng được chọn
        long newUsersInMonth = userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startDate, endDate);

        // DAU chỉ tính nếu là tháng hiện tại, nếu tháng quá khứ thì bằng 0
        long activeUsersToday = isCurrentMonth
                ? userRepository.countByLastActiveAtGreaterThanEqual(LocalDate.now().atStartOfDay())
                : 0L;

        return UserStatsDto.builder()
                .totalUsers(totalUsers)
                .teacherCount(teacherCount)
                .studentCount(studentCount)
                .newUsersThisWeek(newUsersInMonth)
                .activeUsersToday(activeUsersToday)
                .build();
    }

    private ClassroomStatsDto calculateClassroomStats(LocalDateTime endDate) {
        return ClassroomStatsDto.builder()
                .activeClassesCount(classroomRepository.countByCreatedAtLessThan(endDate))
                .build();
    }

    private RevenueStatsDto calculateRevenueStats(LocalDateTime startDate, LocalDateTime endDate, LocalDateTime startOfLastMonth) {
        Long currentMonthRevenue = creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(
                CreditPurchaseOrderStatus.SUCCESS, startDate, endDate);
        long monthlyRevenue = currentMonthRevenue != null ? currentMonthRevenue : 0L;

        Long lastMonthRev = creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(
                CreditPurchaseOrderStatus.SUCCESS, startOfLastMonth, startDate);
        long lastMonthRevenue = lastMonthRev != null ? lastMonthRev : 0L;

        double growth = 0.0;
        if (lastMonthRevenue > 0) {
            growth = ((monthlyRevenue - lastMonthRevenue) * 100.0) / lastMonthRevenue;
        } else if (monthlyRevenue > 0) {
            growth = 100.0;
        }
        double roundedGrowth = Math.round(growth * 10.0) / 10.0;

        long successfulOrdersCount = creditPurchaseOrderRepository.countByStatusAndPaidAtBetween(
                CreditPurchaseOrderStatus.SUCCESS, startDate, endDate);

        return RevenueStatsDto.builder()
                .monthlyRevenue(monthlyRevenue)
                .growthPercentage(roundedGrowth)
                .successfulOrdersCount(successfulOrdersCount)
                .build();
    }

    private BugReportStatsDto calculateBugReportStats(LocalDateTime endDate) {
        long pending = bugReportRepository.countByStatusAndCreatedAtLessThan(BugReportStatus.PENDING, endDate);
        return BugReportStatsDto.builder()
                .pendingCount(pending)
                .build();
    }

    private List<AiTaskUsageDto> calculateAiTaskUsages(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rawTransactions = creditTransactionRepository.countTaskTransactionsByTypesAndCreatedAtBetween(
                List.of(CreditTransactionType.CONSUME, CreditTransactionType.REFUND), startDate, endDate);

        Map<String, Long> taskSuccessMap = new HashMap<>();
        Map<String, Long> taskFailedMap = new HashMap<>();
        long totalSystemCalls = 0;

        for (Object[] row : rawTransactions) {
            if (row[0] != null && row[1] != null && row[2] != null) {
                String task = row[0].toString();
                CreditTransactionType type = (CreditTransactionType) row[1];
                long count = ((Number) row[2]).longValue();

                if (type == CreditTransactionType.CONSUME) {
                    taskSuccessMap.put(task, count);
                } else if (type == CreditTransactionType.REFUND) {
                    taskFailedMap.put(task, count);
                }
                totalSystemCalls += count;
            }
        }

        List<TaskMeta> standardTasks = List.of(
                new TaskMeta("BATCH_QUESTION_GEN", "AI Tách đề thi"),
                new TaskMeta("QUESTION_GEN", "AI Sinh đề & câu hỏi"),
                new TaskMeta("SUBMISSION_GRADING", "AI Chấm bài tự động"),
                new TaskMeta("STUDENT_HINT", "AI Gợi ý giải bài"),
                new TaskMeta("STUDENT_REMARK", "AI Nhận xét học sinh"),
                new TaskMeta("CANVAS_LATEX", "AI Nhận diện hình ảnh & viết tay")
        );

        long finalTotalSystemCalls = totalSystemCalls;
        return standardTasks.stream().map(meta -> {
            long successCount = taskSuccessMap.getOrDefault(meta.code, 0L);
            long failedCount = taskFailedMap.getOrDefault(meta.code, 0L);
            long taskTotalCalls = successCount + failedCount;

            double successRate = taskTotalCalls > 0
                    ? Math.round((successCount * 1000.0) / taskTotalCalls) / 10.0
                    : 100.0;

            double percentage = finalTotalSystemCalls > 0
                    ? Math.round((taskTotalCalls * 1000.0) / finalTotalSystemCalls) / 10.0
                    : 0.0;

            return AiTaskUsageDto.builder()
                    .taskCode(meta.code)
                    .taskName(meta.name)
                    .callCount(taskTotalCalls)
                    .successCount(successCount)
                    .failedCount(failedCount)
                    .successRate(successRate)
                    .percentage(percentage)
                    .build();
        }).collect(Collectors.toList());
    }

    private List<PackageSalesDto> calculatePackageSales(LocalDateTime startDate, LocalDateTime endDate) {
        List<CreditPackage> packages = creditPackageRepository.findAll();
        List<Object[]> purchaseCounts = creditPurchaseOrderRepository.countPurchasesByPackageAndPaidAtBetween(
                CreditPurchaseOrderStatus.SUCCESS, startDate, endDate);
        Map<Long, Long> packageCountMap = new HashMap<>();

        for (Object[] row : purchaseCounts) {
            if (row[0] != null && row[1] != null) {
                Long packageId = ((Number) row[0]).longValue();
                long count = ((Number) row[1]).longValue();
                packageCountMap.put(packageId, count);
            }
        }

        return packages.stream()
                .map(pkg -> PackageSalesDto.builder()
                        .packageId(pkg.getId())
                        .packageName(pkg.getName())
                        .credits(pkg.getCredits())
                        .price(pkg.getPrice())
                        .salesCount(packageCountMap.getOrDefault(pkg.getId(), 0L))
                        .build())
                .sorted(Comparator.comparing(PackageSalesDto::getSalesCount).reversed())
                .collect(Collectors.toList());
    }

    private List<RecentTransactionDto> getTransactions(LocalDateTime startDate, LocalDateTime endDate) {
        List<CreditPurchaseOrder> orders = creditPurchaseOrderRepository
                .findByStatusAndPaidAtBetweenOrderByPaidAtDesc(CreditPurchaseOrderStatus.SUCCESS, startDate, endDate);

        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = orders.stream().map(CreditPurchaseOrder::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<Long> packageIds = orders.stream().map(CreditPurchaseOrder::getPackageId).collect(Collectors.toSet());
        Map<Long, CreditPackage> packageMap = creditPackageRepository.findAllById(packageIds).stream()
                .collect(Collectors.toMap(CreditPackage::getId, p -> p));

        return orders.stream().map(order -> {
            User user = userMap.get(order.getUserId());
            CreditPackage pkg = packageMap.get(order.getPackageId());

            return RecentTransactionDto.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .fullName(user != null ? user.getFullName() : "Người dùng #" + order.getUserId())
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .role(user != null && user.getRole() != null ? user.getRole().name() : "STUDENT")
                    .packageName(pkg != null ? pkg.getName() : "Gói Credit")
                    .price(order.getPrice())
                    .credits(order.getCredits())
                    .status(order.getStatus().name())
                    .paidAt(order.getPaidAt() != null ? order.getPaidAt() : order.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<MonthlyUserTrendDto> calculateUserTrends(LocalDateTime startOfYear, LocalDateTime endOfYear) {
        List<Object[]> rawCounts = userRepository.countNewUsersByMonthOfYear(startOfYear, endOfYear);
        Map<Integer, Long> countMap = new HashMap<>();
        for (Object[] row : rawCounts) {
            if (row[0] != null && row[1] != null) {
                int month = ((Number) row[0]).intValue();
                long count = ((Number) row[1]).longValue();
                countMap.put(month, count);
            }
        }
        List<MonthlyUserTrendDto> trends = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            trends.add(MonthlyUserTrendDto.builder()
                    .month(m)
                    .count(countMap.getOrDefault(m, 0L))
                    .build());
        }
        return trends;
    }

    private List<MonthlyRevenueTrendDto> calculateRevenueTrends(LocalDateTime startOfYear, LocalDateTime endOfYear) {
        List<Object[]> rawRevenues = creditPurchaseOrderRepository.sumRevenueByMonthOfYear(startOfYear, endOfYear);
        Map<Integer, Long> revenueMap = new HashMap<>();
        for (Object[] row : rawRevenues) {
            if (row[0] != null && row[1] != null) {
                int month = ((Number) row[0]).intValue();
                long rev = ((Number) row[1]).longValue();
                revenueMap.put(month, rev);
            }
        }
        List<MonthlyRevenueTrendDto> trends = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            trends.add(MonthlyRevenueTrendDto.builder()
                    .month(m)
                    .revenue(revenueMap.getOrDefault(m, 0L))
                    .build());
        }
        return trends;
    }

    private List<RecentSystemLogDto> getRecentSystemLogs() {
        return systemLogRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(logEntry -> RecentSystemLogDto.builder()
                        .id(logEntry.getId())
                        .actor(logEntry.getActor())
                        .resourceType(logEntry.getResourceType())
                        .action(logEntry.getAction())
                        .level(logEntry.getLevel() != null ? logEntry.getLevel().name() : null)
                        .createdAt(logEntry.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<RecentBugReportDto> getRecentBugReports() {
        return bugReportRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(bug -> RecentBugReportDto.builder()
                        .id(bug.getId())
                        .reporterEmail(bug.getReporterEmail())
                        .errorType(bug.getErrorType() != null ? bug.getErrorType().name() : null)
                        .description(bug.getDescription())
                        .status(bug.getStatus() != null ? bug.getStatus().name() : null)
                        .createdAt(bug.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private record TaskMeta(String code, String name) {}
}
