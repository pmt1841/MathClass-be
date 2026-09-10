package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditTransactionRepository;
import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.dashboard.dto.response.admin.AdminDashboardStatsResponse;
import com.codegym.mathclass.dashboard.service.impl.AdminDashboardServiceImpl;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.entity.SystemLog;
import com.codegym.mathclass.systemlog.repository.SystemLogRepository;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private CreditPurchaseOrderRepository creditPurchaseOrderRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private CreditPackageRepository creditPackageRepository;

    @Mock
    private BugReportRepository bugReportRepository;

    @Mock
    private SystemLogRepository systemLogRepository;

    @InjectMocks
    private AdminDashboardServiceImpl adminDashboardService;

    private User sampleUser;
    private CreditPackage samplePackage;
    private CreditPurchaseOrder sampleOrder;
    private SystemLog sampleLog;
    private BugReport sampleBug;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .fullName("Thầy Nam")
                .email("nam@mathclass.edu.vn")
                .role(Role.TEACHER)
                .build();
        sampleUser.setId(1L);

        samplePackage = CreditPackage.builder()
                .name("Gói Tiêu Chuẩn")
                .credits(600)
                .price(100000)
                .build();
        samplePackage.setId(10L);

        sampleOrder = CreditPurchaseOrder.builder()
                .userId(1L)
                .packageId(10L)
                .price(100000)
                .credits(600)
                .status(CreditPurchaseOrderStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();
        sampleOrder.setId(100L);

        sampleLog = SystemLog.builder()
                .actor("admin@mathclass.edu.vn")
                .resourceType("USER")
                .action("Cập nhật quyền người dùng")
                .level(SystemLogLevel.INFO)
                .build();
        sampleLog.setId(501L);
        sampleLog.setCreatedAt(LocalDateTime.now());

        sampleBug = BugReport.builder()
                .reporterEmail("student@mathclass.edu.vn")
                .errorType(BugErrorType.SUBMISSION_PROBLEM)
                .description("Lỗi không hiển thị bài kiểm tra")
                .status(BugReportStatus.PENDING)
                .build();
        sampleBug.setId(601L);
        sampleBug.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-01: Lấy thành công toàn bộ số liệu Admin Dashboard tháng hiện tại")
    void testGetAdminDashboardStats_Success() {
        // Mock User stats
        when(userRepository.countByCreatedAtLessThan(any())).thenReturn(100L);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.TEACHER), any())).thenReturn(20L);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.STUDENT), any())).thenReturn(80L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(15L);
        when(userRepository.countByLastActiveAtGreaterThanEqual(any())).thenReturn(30L);

        // Mock Classroom stats
        when(classroomRepository.countByCreatedAtLessThan(any())).thenReturn(12L);

        // Mock Revenue stats
        when(creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(5000000L)
                .thenReturn(4000000L);
        when(creditPurchaseOrderRepository.countByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(25L);

        // Mock Bug reports
        when(bugReportRepository.countByStatusAndCreatedAtLessThan(eq(BugReportStatus.PENDING), any())).thenReturn(4L);

        // Mock AI Task usage: row[0]=task, row[1]=totalCalls (CONSUME), row[2]=failedCalls (REFUND do lỗi)
        List<Object[]> rawTaskTransactions = List.<Object[]>of(
                new Object[]{"BATCH_QUESTION_GEN", 60L, 2L},
                new Object[]{"QUESTION_GEN", 40L, 0L}
        );
        when(creditTransactionRepository.countAiCallsAndFailuresByTaskAndCreatedAtBetween(any(), any()))
                .thenReturn(rawTaskTransactions);

        // Mock Package sales & Transactions (tái sử dụng creditPackageRepository.findAll)
        when(creditPackageRepository.findAll()).thenReturn(List.of(samplePackage));
        List<Object[]> rawPackagePurchases = List.<Object[]>of(
                new Object[]{10L, 18L}
        );
        when(creditPurchaseOrderRepository.countPurchasesByPackageAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(rawPackagePurchases);

        // Mock Recent transactions (dùng PageRequest.of(0, 50))
        when(creditPurchaseOrderRepository.findByStatusAndPaidAtBetweenOrderByPaidAtDesc(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any(), any()))
                .thenReturn(List.of(sampleOrder));
        when(userRepository.findAllById(any(Set.class))).thenReturn(List.of(sampleUser));

        // Mock Annual Trends
        when(userRepository.countNewUsersByMonthOfYear(any(), any())).thenReturn(List.of(
                new Object[]{8, 25L},
                new Object[]{9, 15L}
        ));
        when(creditPurchaseOrderRepository.sumRevenueByMonthOfYear(any(), any())).thenReturn(List.of(
                new Object[]{8, 4000000L},
                new Object[]{9, 5000000L}
        ));

        // Mock Recent Logs & Bug Reports
        when(systemLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(sampleLog));
        when(bugReportRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(sampleBug));

        // When
        java.time.LocalDate now = java.time.LocalDate.now();
        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(now.getMonthValue(), now.getYear());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSelectedMonth()).isEqualTo(now.getMonthValue());
        assertThat(response.getSelectedYear()).isEqualTo(now.getYear());
        assertThat(response.getUserStats().getTotalUsers()).isEqualTo(100L);
        assertThat(response.getUserStats().getTeacherCount()).isEqualTo(20L);
        assertThat(response.getUserStats().getStudentCount()).isEqualTo(80L);
        assertThat(response.getUserStats().getNewUsersInMonth()).isEqualTo(15L);
        assertThat(response.getUserStats().getActiveUsersToday()).isEqualTo(30L);
        assertThat(response.getClassroomStats().getActiveClassesCount()).isEqualTo(12L);
        assertThat(response.getRevenueStats().getMonthlyRevenue()).isEqualTo(5000000L);
        assertThat(response.getBugReportStats().getPendingCount()).isEqualTo(4L);
        assertThat(response.getAiTaskUsages()).hasSize(6);

        var batchTask = response.getAiTaskUsages().stream()
                .filter(t -> "BATCH_QUESTION_GEN".equals(t.getTaskCode()))
                .findFirst().orElseThrow();
        assertThat(batchTask.getCallCount()).isEqualTo(60L);
        assertThat(batchTask.getSuccessCount()).isEqualTo(58L);
        assertThat(batchTask.getFailedCount()).isEqualTo(2L);
        assertThat(batchTask.getSuccessRate()).isEqualTo(96.7);

        assertThat(response.getPackageSales()).hasSize(1);
        assertThat(response.getPackageSales().get(0).getSalesCount()).isEqualTo(18L);
        assertThat(response.getRecentTransactions()).hasSize(1);
        assertThat(response.getRecentTransactions().get(0).getFullName()).isEqualTo("Thầy Nam");

        assertThat(response.getUserTrends()).hasSize(12);
        assertThat(response.getUserTrends().get(7).getMonth()).isEqualTo(8);
        assertThat(response.getUserTrends().get(7).getCount()).isEqualTo(25L);
        assertThat(response.getUserTrends().get(0).getCount()).isEqualTo(0L);

        assertThat(response.getRevenueTrends()).hasSize(12);
        assertThat(response.getRevenueTrends().get(8).getMonth()).isEqualTo(9);
        assertThat(response.getRevenueTrends().get(8).getRevenue()).isEqualTo(5000000L);

        assertThat(response.getRecentSystemLogs()).hasSize(1);
        assertThat(response.getRecentSystemLogs().get(0).getActor()).isEqualTo("admin@mathclass.edu.vn");
        assertThat(response.getRecentSystemLogs().get(0).getResourceType()).isEqualTo("USER");
        assertThat(response.getRecentBugReports()).hasSize(1);
        assertThat(response.getRecentBugReports().get(0).getReporterEmail()).isEqualTo("student@mathclass.edu.vn");
        assertThat(response.getRecentBugReports().get(0).getErrorType()).isEqualTo("SUBMISSION_PROBLEM");
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-02: Lấy số liệu Admin Dashboard cho tháng quá khứ (lọc đúng theo mốc thời gian)")
    void testGetAdminDashboardStats_PastMonth() {
        when(userRepository.countByCreatedAtLessThan(any())).thenReturn(80L);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.TEACHER), any())).thenReturn(15L);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.STUDENT), any())).thenReturn(65L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(10L);
        when(classroomRepository.countByCreatedAtLessThan(any())).thenReturn(8L);
        when(bugReportRepository.countByStatusAndCreatedAtLessThan(eq(BugReportStatus.PENDING), any())).thenReturn(2L);

        when(creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(3000000L)
                .thenReturn(2000000L);
        when(creditPurchaseOrderRepository.countByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(15L);
        when(creditTransactionRepository.countAiCallsAndFailuresByTaskAndCreatedAtBetween(any(), any()))
                .thenReturn(Collections.emptyList());
        when(creditPackageRepository.findAll()).thenReturn(List.of(samplePackage));
        when(creditPurchaseOrderRepository.countPurchasesByPackageAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(Collections.emptyList());
        when(creditPurchaseOrderRepository.findByStatusAndPaidAtBetweenOrderByPaidAtDesc(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.countNewUsersByMonthOfYear(any(), any())).thenReturn(Collections.emptyList());
        when(creditPurchaseOrderRepository.sumRevenueByMonthOfYear(any(), any())).thenReturn(Collections.emptyList());
        when(systemLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
        when(bugReportRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        // When: chọn tháng 1 năm 2024 (chắc chắn là quá khứ)
        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(1, 2024);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSelectedMonth()).isEqualTo(1);
        assertThat(response.getSelectedYear()).isEqualTo(2024);
        assertThat(response.getUserStats().getTotalUsers()).isEqualTo(80L);
        assertThat(response.getUserStats().getTeacherCount()).isEqualTo(15L);
        assertThat(response.getUserStats().getStudentCount()).isEqualTo(65L);
        assertThat(response.getUserStats().getNewUsersInMonth()).isEqualTo(10L);
        assertThat(response.getUserStats().getActiveUsersToday()).isEqualTo(0L); // Quá khứ không có DAU hôm nay
        assertThat(response.getClassroomStats().getActiveClassesCount()).isEqualTo(8L);
        assertThat(response.getRevenueStats().getMonthlyRevenue()).isEqualTo(3000000L);
        assertThat(response.getBugReportStats().getPendingCount()).isEqualTo(2L);
        assertThat(response.getUserTrends()).hasSize(12);
        assertThat(response.getRevenueTrends()).hasSize(12);
        assertThat(response.getRecentSystemLogs()).isEmpty();
        assertThat(response.getRecentBugReports()).isEmpty();
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-03: Tính % tăng trưởng doanh thu khi tháng trước = 0, tháng này > 0 (kỳ vọng 100.0%)")
    void testRevenueGrowth_LastMonthZero_CurrentMonthPositive() {
        mockDefaultEmptyStats();
        when(creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(5000000L) // Tháng này
                .thenReturn(0L);       // Tháng trước

        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(9, 2026);

        assertThat(response).isNotNull();
        assertThat(response.getRevenueStats().getMonthlyRevenue()).isEqualTo(5000000L);
        assertThat(response.getRevenueStats().getGrowthPercentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-04: Tính % tăng trưởng doanh thu khi cả 2 tháng = 0 (kỳ vọng 0.0%)")
    void testRevenueGrowth_BothMonthsZero() {
        mockDefaultEmptyStats();
        when(creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(0L) // Tháng này
                .thenReturn(0L); // Tháng trước

        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(9, 2026);

        assertThat(response).isNotNull();
        assertThat(response.getRevenueStats().getMonthlyRevenue()).isEqualTo(0L);
        assertThat(response.getRevenueStats().getGrowthPercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-05: Tính % tăng trưởng doanh thu âm khi tháng này giảm so với tháng trước (kỳ vọng -25.5%)")
    void testRevenueGrowth_NegativeGrowth() {
        mockDefaultEmptyStats();
        // (14,900,000 - 20,000,000) / 20,000,000 * 100 = -25.5%
        when(creditPurchaseOrderRepository.sumPriceByStatusAndPaidAtBetween(eq(CreditPurchaseOrderStatus.SUCCESS), any(), any()))
                .thenReturn(14900000L) // Tháng này
                .thenReturn(20000000L); // Tháng trước

        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(9, 2026);

        assertThat(response).isNotNull();
        assertThat(response.getRevenueStats().getMonthlyRevenue()).isEqualTo(14900000L);
        assertThat(response.getRevenueStats().getGrowthPercentage()).isEqualTo(-25.5);
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-06: Phòng vệ tham số đầu vào khi month = null, year = null (kỳ vọng tự fallback về hiện tại)")
    void testParameterFallback_NullInputs() {
        mockDefaultEmptyStats();

        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(null, null);

        java.time.LocalDate now = java.time.LocalDate.now();
        assertThat(response).isNotNull();
        assertThat(response.getSelectedMonth()).isEqualTo(now.getMonthValue());
        assertThat(response.getSelectedYear()).isEqualTo(now.getYear());
    }

    @Test
    @DisplayName("UT-ADMIN-DASH-07: Phòng vệ tham số đầu vào khi month = 15, year = 1990 (kỳ vọng tự đưa về tháng/năm hợp lệ)")
    void testParameterFallback_InvalidInputs() {
        mockDefaultEmptyStats();

        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(15, 1990);

        java.time.LocalDate now = java.time.LocalDate.now();
        assertThat(response).isNotNull();
        assertThat(response.getSelectedMonth()).isEqualTo(now.getMonthValue());
        assertThat(response.getSelectedYear()).isEqualTo(now.getYear());
    }

    private void mockDefaultEmptyStats() {
        when(userRepository.countByCreatedAtLessThan(any())).thenReturn(0L);
        when(userRepository.countByRoleAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(classroomRepository.countByCreatedAtLessThan(any())).thenReturn(0L);
        when(bugReportRepository.countByStatusAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(creditPurchaseOrderRepository.countByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(any(), any(), any())).thenReturn(0L);
        when(creditTransactionRepository.countAiCallsAndFailuresByTaskAndCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(creditPackageRepository.findAll()).thenReturn(Collections.emptyList());
        when(creditPurchaseOrderRepository.countPurchasesByPackageAndPaidAtBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(creditPurchaseOrderRepository.findByStatusAndPaidAtBetweenOrderByPaidAtDesc(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(userRepository.countNewUsersByMonthOfYear(any(), any())).thenReturn(Collections.emptyList());
        when(creditPurchaseOrderRepository.sumRevenueByMonthOfYear(any(), any())).thenReturn(Collections.emptyList());
        when(systemLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
        when(bugReportRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
    }
}
