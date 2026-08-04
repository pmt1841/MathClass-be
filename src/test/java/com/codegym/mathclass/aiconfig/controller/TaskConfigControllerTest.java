package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.TaskConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.TaskConfigResponse;
import com.codegym.mathclass.aiconfig.service.TaskConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskConfigControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TaskConfigService taskConfigService;

    @InjectMocks
    private TaskConfigController taskConfigController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskConfigController).build();
    }

    @Test
    @DisplayName("TC-TASK-CTRL-01: GET /tasks/{task} trả về thông tin cấu hình task")
    void testGetTaskConfig_Success() throws Exception {
        TaskConfigResponse response = TaskConfigResponse.builder()
                .task("QUESTION_GEN")
                .providerId(10L)
                .model("gemini-1.5-flash")
                .temperature(new BigDecimal("0.7"))
                .maxToken(2048)
                .enabled(true)
                .build();

        when(taskConfigService.getTaskConfig("QUESTION_GEN")).thenReturn(response);

        mockMvc.perform(get("/tasks/QUESTION_GEN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value("QUESTION_GEN"))
                .andExpect(jsonPath("$.model").value("gemini-1.5-flash"))
                .andExpect(jsonPath("$.providerId").value(10));
    }

    @Test
    @DisplayName("TC-TASK-CTRL-02: PUT /tasks/{task} cập nhật cấu hình task thành công")
    void testUpdateTaskConfig_Success() throws Exception {
        TaskConfigUpdateRequest request = TaskConfigUpdateRequest.builder()
                .providerId(10L)
                .model("gemini-1.5-pro")
                .temperature(new BigDecimal("0.2"))
                .maxToken(4096)
                .enabled(true)
                .build();

        TaskConfigResponse response = TaskConfigResponse.builder()
                .task("QUESTION_GEN")
                .providerId(10L)
                .model("gemini-1.5-pro")
                .temperature(new BigDecimal("0.2"))
                .maxToken(4096)
                .enabled(true)
                .build();

        when(taskConfigService.updateTaskConfig(eq("QUESTION_GEN"), any(TaskConfigUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/tasks/QUESTION_GEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value("QUESTION_GEN"))
                .andExpect(jsonPath("$.model").value("gemini-1.5-pro"))
                .andExpect(jsonPath("$.temperature").value(0.2));
    }
}
