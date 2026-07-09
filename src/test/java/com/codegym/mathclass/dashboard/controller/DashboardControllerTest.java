package com.codegym.mathclass.dashboard.controller;

import com.codegym.mathclass.dashboard.dto.*;
import com.codegym.mathclass.dashboard.service.DashboardService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                1L, "User", "user@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(dashboardController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockUserDetails;
                    }
                })
                .build();
    }

    @Test
    @DisplayName("Should return teacher stats")
    void getTeacherStats_ValidRequest_ReturnsOk() throws Exception {
        // Given
        TeacherDashboardStatsDto stats = new TeacherDashboardStatsDto();
        stats.setTeachingClasses(5);
        stats.setManagedStudents(100);
        stats.setOpenAssignments(2);
        stats.setAssignmentsToGrade(10);

        when(dashboardService.getTeacherDashboardStats(1L)).thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/dashboard/teacher-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingClasses").value(5))
                .andExpect(jsonPath("$.managedStudents").value(100))
                .andExpect(jsonPath("$.openAssignments").value(2))
                .andExpect(jsonPath("$.assignmentsToGrade").value(10));

        verify(dashboardService, times(1)).getTeacherDashboardStats(1L);
    }

    @Test
    @DisplayName("Should return pending submissions for teacher")
    void getPendingSubmissions_ValidRequest_ReturnsOk() throws Exception {
        // Given
        PendingSubmissionDto dto = new PendingSubmissionDto();
        dto.setId(100L);
        dto.setStudentName("John Doe");

        when(dashboardService.getPendingSubmissions(1L, 10)).thenReturn(Collections.singletonList(dto));

        // When & Then
        mockMvc.perform(get("/api/dashboard/pending-submissions").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].studentName").value("John Doe"));

        verify(dashboardService, times(1)).getPendingSubmissions(1L, 10);
    }

    @Test
    @DisplayName("Should return at-risk students for teacher")
    void getAtRiskStudents_ValidRequest_ReturnsOk() throws Exception {
        // Given
        AtRiskStudentDto dto = new AtRiskStudentDto();
        dto.setId(2L);
        dto.setName("Jane Doe");

        when(dashboardService.getAtRiskStudents(1L)).thenReturn(Collections.singletonList(dto));

        // When & Then
        mockMvc.perform(get("/api/dashboard/at-risk-students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].name").value("Jane Doe"));

        verify(dashboardService, times(1)).getAtRiskStudents(1L);
    }

    @Test
    @DisplayName("Should return student stats")
    void getStudentStats_ValidRequest_ReturnsOk() throws Exception {
        // Given
        StudentDashboardStatsDto stats = new StudentDashboardStatsDto();
        stats.setJoinedClasses(3);
        stats.setPendingTasks(5);
        stats.setCompletedTasks(15);

        when(dashboardService.getStudentDashboardStats(1L)).thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/dashboard/student-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinedClasses").value(3))
                .andExpect(jsonPath("$.pendingTasks").value(5))
                .andExpect(jsonPath("$.completedTasks").value(15));

        verify(dashboardService, times(1)).getStudentDashboardStats(1L);
    }

    @Test
    @DisplayName("Should return student pending tasks")
    void getStudentPendingTasks_ValidRequest_ReturnsOk() throws Exception {
        // Given
        StudentPendingTaskDto dto = new StudentPendingTaskDto();
        dto.setId(10L);
        dto.setTitle("Math Homework");

        when(dashboardService.getStudentPendingTasks(1L, 10)).thenReturn(Collections.singletonList(dto));

        // When & Then
        mockMvc.perform(get("/api/dashboard/student-pending-tasks").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].title").value("Math Homework"));

        verify(dashboardService, times(1)).getStudentPendingTasks(1L, 10);
    }

    @Test
    @DisplayName("Should return student graded tasks")
    void getStudentGradedTasks_ValidRequest_ReturnsOk() throws Exception {
        // Given
        StudentGradedTaskDto dto = new StudentGradedTaskDto();
        dto.setId(100L);
        dto.setScore(9.5f);

        when(dashboardService.getStudentGradedTasks(1L, 10)).thenReturn(Collections.singletonList(dto));

        // When & Then
        mockMvc.perform(get("/api/dashboard/student-graded-tasks").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].score").value(9.5));

        verify(dashboardService, times(1)).getStudentGradedTasks(1L, 10);
    }
}
