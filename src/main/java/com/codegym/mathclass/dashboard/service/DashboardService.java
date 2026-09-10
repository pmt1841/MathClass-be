package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.dashboard.dto.response.TeacherDashboardStatsResponse;
import com.codegym.mathclass.dashboard.dto.response.PendingSubmissionResponse;
import com.codegym.mathclass.dashboard.dto.response.StudentDashboardStatsResponse;
import com.codegym.mathclass.dashboard.dto.response.StudentPendingTaskResponse;
import com.codegym.mathclass.dashboard.dto.response.StudentGradedTaskResponse;
import com.codegym.mathclass.dashboard.dto.response.AtRiskStudentResponse;
import java.util.List;

public interface DashboardService {
    TeacherDashboardStatsResponse getTeacherDashboardStats(long teacherId);
    List<PendingSubmissionResponse> getPendingSubmissions(long teacherId, int limit);
    
    StudentDashboardStatsResponse getStudentDashboardStats(long studentId);
    List<StudentPendingTaskResponse> getStudentPendingTasks(long studentId, int limit);
    List<StudentGradedTaskResponse> getStudentGradedTasks(long studentId, int limit);

    List<AtRiskStudentResponse> getAtRiskStudents(long teacherId);
}
