package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.dashboard.dto.TeacherDashboardStatsDto;

public interface DashboardService {
    TeacherDashboardStatsDto getTeacherDashboardStats(long teacherId);
}
