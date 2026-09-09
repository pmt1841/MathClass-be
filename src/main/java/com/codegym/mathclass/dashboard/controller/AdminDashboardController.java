package com.codegym.mathclass.dashboard.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.common.dto.ApiResponse;
import com.codegym.mathclass.dashboard.dto.admin.AdminDashboardStatsResponse;
import com.codegym.mathclass.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Admin - Dashboard", description = "APIs quản trị viên: Thống kê tổng quan KPI, Doanh thu, AI và Hoạt động")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "Thống kê tổng quan Quản trị viên (Admin Dashboard Stats)",
            description = "Lấy toàn bộ số liệu KPI người dùng, lớp học, doanh thu tháng, báo cáo sự cố, phân bổ các tác vụ AI và giao dịch theo tháng/năm")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> getDashboardStats(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        AdminDashboardStatsResponse response = adminDashboardService.getAdminDashboardStats(month, year);
        return ResponseEntity.ok(ApiResponse.<AdminDashboardStatsResponse>builder()
                .code(1000)
                .message("Lấy thống kê Admin Dashboard thành công")
                .result(response)
                .build());
    }
}
