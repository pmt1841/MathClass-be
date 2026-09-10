package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.dashboard.dto.response.admin.AdminDashboardStatsResponse;

public interface AdminDashboardService {
    AdminDashboardStatsResponse getAdminDashboardStats(Integer month, Integer year);
}
