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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Dashboard", description = "APIs bảng điều khiển thống kê cho Giáo viên và Học sinh")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Thống kê Dashboard Giáo viên", description = "Lấy tổng số lượng bài tập, lớp học, học sinh và bài nộp chờ chấm của giáo viên")
    @GetMapping("/teacher-stats")
    @PreAuthorize("hasAuthority('dashboard:teacher_view')")
    public ResponseEntity<TeacherDashboardStatsDto> getTeacherStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getTeacherDashboardStats(userDetails.getId()));
    }

    @Operation(summary = "Danh sách bài nộp chờ chấm điểm", description = "Lấy danh sách các bài làm của học sinh đang chờ giáo viên chấm điểm")
    @GetMapping("/pending-submissions")
    @PreAuthorize("hasAuthority('dashboard:teacher_view')")
    public ResponseEntity<List<PendingSubmissionDto>> getPendingSubmissions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getPendingSubmissions(userDetails.getId(), limit));
    }

    @Operation(summary = "Danh sách học sinh có nguy cơ cần hỗ trợ", description = "Cảnh báo các học sinh có điểm trung bình thấp hoặc bỏ nộp bài")
    @GetMapping("/at-risk-students")
    @PreAuthorize("hasAuthority('dashboard:teacher_view')")
    public ResponseEntity<List<AtRiskStudentDto>> getAtRiskStudents(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getAtRiskStudents(userDetails.getId()));
    }

    @Operation(summary = "Thống kê Dashboard Học sinh", description = "Lấy tổng số bài cần nộp, số bài đã hoàn thành và điểm trung bình của học sinh")
    @GetMapping("/student-stats")
    @PreAuthorize("hasAuthority('dashboard:student_view')")
    public ResponseEntity<StudentDashboardStatsDto> getStudentStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(dashboardService.getStudentDashboardStats(userDetails.getId()));
    }

    @Operation(summary = "Danh sách bài tập cần làm (Học sinh)", description = "Lấy danh sách bài tập chưa nộp hoặc sắp đến hạn nộp bài")
    @GetMapping("/student-pending-tasks")
    @PreAuthorize("hasAuthority('dashboard:student_view')")
    public ResponseEntity<List<StudentPendingTaskDto>> getStudentPendingTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getStudentPendingTasks(userDetails.getId(), limit));
    }

    @Operation(summary = "Danh sách bài tập đã được chấm điểm (Học sinh)", description = "Lấy danh sách kết quả và điểm số các bài nộp vừa được giáo viên chấm")
    @GetMapping("/student-graded-tasks")
    @PreAuthorize("hasAuthority('dashboard:student_view')")
    public ResponseEntity<List<StudentGradedTaskDto>> getStudentGradedTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getStudentGradedTasks(userDetails.getId(), limit));
    }
}
