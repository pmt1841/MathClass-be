package com.codegym.mathclass.systemlog.controller;

import com.codegym.mathclass.systemlog.dto.response.SystemLogResponse;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private AdminLogController adminLogController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminLogController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /admin/logs - Should return paginated system logs with filters")
    void getLogs_Success() throws Exception {
        SystemLogResponse log = SystemLogResponse.builder()
                .id(1L)
                .timestamp(LocalDateTime.now())
                .actor("admin@mathclass.edu.vn")
                .action("UPDATE_AI_CONFIG")
                .level(SystemLogLevel.INFO)
                .resourceType("AI_CONFIG")
                .resourceId("1")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .status("SUCCESS")
                .build();

        Page<SystemLogResponse> page = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);

        when(systemLogService.getLogs(
                eq(SystemLogLevel.INFO),
                eq("AI_CONFIG"),
                eq("admin@mathclass.edu.vn"),
                any(),
                any(),
                any(Pageable.class)
        )).thenReturn(page);

        mockMvc.perform(get("/admin/logs")
                        .param("level", "INFO")
                        .param("resourceType", "AI_CONFIG")
                        .param("actor", "admin@mathclass.edu.vn")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].actor").value("admin@mathclass.edu.vn"))
                .andExpect(jsonPath("$.content[0].action").value("UPDATE_AI_CONFIG"))
                .andExpect(jsonPath("$.content[0].level").value("INFO"))
                .andExpect(jsonPath("$.content[0].resourceType").value("AI_CONFIG"));
    }
}
