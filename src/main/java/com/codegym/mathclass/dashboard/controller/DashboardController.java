package com.codegym.mathclass.dashboard.controller;

import com.codegym.mathclass.dashboard.dto.TeacherDashboardStatsDto;
import com.codegym.mathclass.dashboard.service.DashboardService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.codegym.mathclass.dashboard.dto.PendingSubmissionDto;
import com.codegym.mathclass.dashboard.dto.StudentDashboardStatsDto;
import com.codegym.mathclass.dashboard.dto.StudentPendingTaskDto;
import com.codegym.mathclass.dashboard.dto.StudentGradedTaskDto;
import com.codegym.mathclass.dashboard.dto.AtRiskStudentDto;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/teacher-stats")
    @PreAuthorize("hasAuthority('dashboard:teacher_view')")
    public ResponseEntity<TeacherDashboardStatsDto> getTeacherStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getTeacherDashboardStats(userDetails.getId()));
    }

    @GetMapping("/pending-submissions")
    @PreAuthorize("hasAuthority('dashboard:teacher_view')")
    public ResponseEntity<List<PendingSubmissionDto>> getPendingSubmissions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getPendingSubmissions(userDetails.getId(), limit));
    }

    @GetMapping("/at-risk-students")
    @PreAuthorize("hasAuthority('dashboard:teacher_view')")
    public ResponseEntity<List<AtRiskStudentDto>> getAtRiskStudents(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getAtRiskStudents(userDetails.getId()));
    }

    @GetMapping("/student-stats")
    @PreAuthorize("hasAuthority('dashboard:student_view')")
    public ResponseEntity<StudentDashboardStatsDto> getStudentStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getStudentDashboardStats(userDetails.getId()));
    }

    @GetMapping("/student-pending-tasks")
    @PreAuthorize("hasAuthority('dashboard:student_view')")
    public ResponseEntity<List<StudentPendingTaskDto>> getStudentPendingTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getStudentPendingTasks(userDetails.getId(), limit));
    }

    @GetMapping("/student-graded-tasks")
    @PreAuthorize("hasAuthority('dashboard:student_view')")
    public ResponseEntity<List<StudentGradedTaskDto>> getStudentGradedTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getStudentGradedTasks(userDetails.getId(), limit));
    }
}
