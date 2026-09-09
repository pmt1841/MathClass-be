package com.codegym.mathclass.dashboard.controller;

import com.codegym.mathclass.common.dto.ApiResponse;
import com.codegym.mathclass.dashboard.dto.admin.AdminDashboardStatsResponse;
import com.codegym.mathclass.dashboard.dto.admin.UserStatsDto;
import com.codegym.mathclass.dashboard.service.AdminDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @Test
    @DisplayName("UT-ADMIN-DASH-CTRL-01: Endpoint /stats trả về HTTP 200 và bọc ApiResponse chuẩn")
    void testGetDashboardStats_Success() {
        // Given
        AdminDashboardStatsResponse mockResponse = AdminDashboardStatsResponse.builder()
                .userStats(UserStatsDto.builder().totalUsers(150L).build())
                .build();
        when(adminDashboardService.getAdminDashboardStats(8, 2026)).thenReturn(mockResponse);

        // When
        ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> entity = adminDashboardController.getDashboardStats(8, 2026);

        // Then
        assertThat(entity.getStatusCode().value()).isEqualTo(200);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getCode()).isEqualTo(1000);
        assertThat(entity.getBody().getResult()).isEqualTo(mockResponse);
        assertThat(entity.getBody().getResult().getUserStats().getTotalUsers()).isEqualTo(150L);
    }
}
