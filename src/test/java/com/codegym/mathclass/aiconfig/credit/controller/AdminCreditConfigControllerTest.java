package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.DefaultCreditUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.TaskCreditConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.AiCreditConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.DefaultCreditResponse;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCreditConfigControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiCreditService aiCreditService;

    @InjectMocks
    private AdminCreditConfigController adminCreditConfigController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminCreditConfigController).build();
    }

    @Nested
    @DisplayName("Task Credit Config Tests")
    class TaskCreditConfigTests {

        @Test
        @DisplayName("GET /admin/ai-credit-config/tasks - Should return list of task credit configs")
        void listTaskConfigs_Success() throws Exception {
            AiCreditConfigResponse config = AiCreditConfigResponse.builder()
                    .id(1L)
                    .task("QUESTION_GEN")
                    .costPerCall(2)
                    .tokensPerCredit(1000)
                    .enabled(true)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(aiCreditService.getAllCreditConfigs()).thenReturn(List.of(config));

            mockMvc.perform(get("/admin/ai-credit-config/tasks")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1L))
                    .andExpect(jsonPath("$[0].task").value("QUESTION_GEN"))
                    .andExpect(jsonPath("$[0].costPerCall").value(2))
                    .andExpect(jsonPath("$[0].enabled").value(true));
        }

        @Test
        @DisplayName("PUT /admin/ai-credit-config/tasks/{task} - Should update task credit config")
        void updateTaskConfig_Success() throws Exception {
            TaskCreditConfigUpdateRequest request = TaskCreditConfigUpdateRequest.builder()
                    .costPerCall(3)
                    .tokensPerCredit(800)
                    .enabled(true)
                    .build();

            AiCreditConfigResponse response = AiCreditConfigResponse.builder()
                    .id(1L)
                    .task("QUESTION_GEN")
                    .costPerCall(3)
                    .tokensPerCredit(800)
                    .enabled(true)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(aiCreditService.updateCreditConfig("QUESTION_GEN", 3, 800, true))
                    .thenReturn(response);

            mockMvc.perform(put("/admin/ai-credit-config/tasks/QUESTION_GEN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").doesNotExist()) // single object
                    .andExpect(jsonPath("$.task").value("QUESTION_GEN"))
                    .andExpect(jsonPath("$.costPerCall").value(3))
                    .andExpect(jsonPath("$.tokensPerCredit").value(800));
        }
    }

    @Nested
    @DisplayName("Default Credit Tests")
    class DefaultCreditTests {

        @Test
        @DisplayName("GET /admin/ai-credit-config/defaults - Should return default credits by role")
        void listDefaults_Success() throws Exception {
            DefaultCreditResponse response = DefaultCreditResponse.builder()
                    .role("TEACHER")
                    .defaultCredits(100)
                    .build();

            when(aiCreditService.getAllDefaults()).thenReturn(List.of(response));

            mockMvc.perform(get("/admin/ai-credit-config/defaults")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].role").value("TEACHER"))
                    .andExpect(jsonPath("$[0].defaultCredits").value(100));
        }

        @Test
        @DisplayName("PUT /admin/ai-credit-config/defaults/{role} - Should update default credits for role")
        void updateDefault_Success() throws Exception {
            DefaultCreditUpdateRequest request = DefaultCreditUpdateRequest.builder()
                    .defaultCredits(150)
                    .build();

            DefaultCreditResponse response = DefaultCreditResponse.builder()
                    .role("TEACHER")
                    .defaultCredits(150)
                    .build();

            when(aiCreditService.updateDefaultCredits(eq(Role.TEACHER), eq(150)))
                    .thenReturn(response);

            mockMvc.perform(put("/admin/ai-credit-config/defaults/TEACHER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("TEACHER"))
                    .andExpect(jsonPath("$.defaultCredits").value(150));
        }
    }
}
