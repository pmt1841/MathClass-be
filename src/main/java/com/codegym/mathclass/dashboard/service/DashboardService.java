package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.dashboard.dto.TeacherDashboardStatsDto;
import com.codegym.mathclass.dashboard.dto.PendingSubmissionDto;
import com.codegym.mathclass.dashboard.dto.StudentDashboardStatsDto;
import com.codegym.mathclass.dashboard.dto.StudentPendingTaskDto;
import com.codegym.mathclass.dashboard.dto.StudentGradedTaskDto;
import com.codegym.mathclass.dashboard.dto.AtRiskStudentDto;
import java.util.List;

public interface DashboardService {
    TeacherDashboardStatsDto getTeacherDashboardStats(long teacherId);
    List<PendingSubmissionDto> getPendingSubmissions(long teacherId, int limit);
    
    StudentDashboardStatsDto getStudentDashboardStats(long studentId);
    List<StudentPendingTaskDto> getStudentPendingTasks(long studentId, int limit);
    List<StudentGradedTaskDto> getStudentGradedTasks(long studentId, int limit);

    List<AtRiskStudentDto> getAtRiskStudents(long teacherId);
}
