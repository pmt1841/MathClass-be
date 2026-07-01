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

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/teacher-stats")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<TeacherDashboardStatsDto> getTeacherStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getTeacherDashboardStats(userDetails.getId()));
    }

    @GetMapping("/pending-submissions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<java.util.List<com.codegym.mathclass.dashboard.dto.PendingSubmissionDto>> getPendingSubmissions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getPendingSubmissions(userDetails.getId(), limit));
    }

    @GetMapping("/student-stats")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<com.codegym.mathclass.dashboard.dto.StudentDashboardStatsDto> getStudentStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getStudentDashboardStats(userDetails.getId()));
    }

    @GetMapping("/student-pending-tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<java.util.List<com.codegym.mathclass.dashboard.dto.StudentPendingTaskDto>> getStudentPendingTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getStudentPendingTasks(userDetails.getId(), limit));
    }

    @GetMapping("/student-graded-tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<java.util.List<com.codegym.mathclass.dashboard.dto.StudentGradedTaskDto>> getStudentGradedTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getStudentGradedTasks(userDetails.getId(), limit));
    }
}
