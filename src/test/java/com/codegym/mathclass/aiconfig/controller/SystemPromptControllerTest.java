package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.PromptTestExecuteRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptResetRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.PromptTestExecuteResponse;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptHistoryResponse;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptResponse;
import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import com.codegym.mathclass.aiconfig.service.SystemPromptService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SystemPromptControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SystemPromptService systemPromptService;

    @InjectMocks
    private SystemPromptController systemPromptController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(systemPromptController).build();
    }

    private SystemPromptResponse buildPromptResponse(Long id, String code, String name) {
        return SystemPromptResponse.builder()
                .id(id)
                .code(code)
                .name(name)
                .taskCode("QUESTION_GEN")
                .defaultContent("Default content {{topic}}")
                .currentContent("Current content {{topic}}")
                .allowedVariables(List.of("topic"))
                .status(SystemPromptStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("GET /system-prompts Tests")
    class GetAllPromptsTests {

        @Test
        @DisplayName("Should return list of prompts with filters")
        void getAllPrompts_Success() throws Exception {
            SystemPromptResponse response = buildPromptResponse(1L, "PROMPT_GEN_1", "Question Generator Prompt");
            when(systemPromptService.getAllPrompts(eq("QUESTION_GEN"), eq("Generator")))
                    .thenReturn(List.of(response));

            mockMvc.perform(get("/system-prompts")
                            .param("taskCode", "QUESTION_GEN")
                            .param("search", "Generator")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(1L))
                    .andExpect(jsonPath("$.data[0].code").value("PROMPT_GEN_1"))
                    .andExpect(jsonPath("$.data[0].taskCode").value("QUESTION_GEN"));
        }
    }

    @Nested
    @DisplayName("GET /system-prompts/{id} Tests")
    class GetPromptByIdTests {

        @Test
        @DisplayName("Should return prompt details by id")
        void getPromptById_Success() throws Exception {
            SystemPromptResponse response = buildPromptResponse(1L, "PROMPT_GEN_1", "Question Generator Prompt");
            when(systemPromptService.getPromptById(1L)).thenReturn(response);

            mockMvc.perform(get("/system-prompts/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.name").value("Question Generator Prompt"))
                    .andExpect(jsonPath("$.currentContent").value("Current content {{topic}}"));
        }
    }

    @Nested
    @DisplayName("PUT /system-prompts/{id} Tests")
    class UpdatePromptTests {

        @Test
        @DisplayName("Should update prompt and return 200 OK")
        void updatePrompt_Success() throws Exception {
            SystemPromptUpdateRequest request = SystemPromptUpdateRequest.builder()
                    .name("Updated Prompt")
                    .currentContent("New current content {{topic}}")
                    .description("Updated desc")
                    .changeReason("Refined instructions")
                    .build();

            SystemPromptResponse response = buildPromptResponse(1L, "PROMPT_GEN_1", "Updated Prompt");
            response.setCurrentContent("New current content {{topic}}");

            when(systemPromptService.updatePrompt(eq(1L), any(SystemPromptUpdateRequest.class), any(), any()))
                    .thenReturn(response);

            mockMvc.perform(put("/system-prompts/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.name").value("Updated Prompt"))
                    .andExpect(jsonPath("$.currentContent").value("New current content {{topic}}"));
        }
    }

    @Nested
    @DisplayName("POST /system-prompts/{id}/reset Tests")
    class ResetToDefaultTests {

        @Test
        @DisplayName("Should reset prompt to default content and return 200 OK")
        void resetToDefault_Success() throws Exception {
            SystemPromptResetRequest request = new SystemPromptResetRequest();
            request.setReason("Reset to original");

            SystemPromptResponse response = buildPromptResponse(1L, "PROMPT_GEN_1", "Question Generator Prompt");
            response.setCurrentContent("Default content {{topic}}");
            response.setStatus(SystemPromptStatus.ACTIVE);

            when(systemPromptService.resetToDefault(eq(1L), any(), any(), any()))
                    .thenReturn(response);

            mockMvc.perform(post("/system-prompts/1/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("GET /system-prompts/{id}/history Tests")
    class GetPromptHistoryTests {

        @Test
        @DisplayName("Should return prompt version history")
        void getPromptHistory_Success() throws Exception {
            SystemPromptHistoryResponse history = SystemPromptHistoryResponse.builder()
                    .id(100L)
                    .promptId(1L)
                    .version(1)
                    .content("Old version content")
                    .changeReason("Initial version")
                    .createdBy("admin@mathclass.edu.vn")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(systemPromptService.getPromptHistory(1L)).thenReturn(List.of(history));

            mockMvc.perform(get("/system-prompts/1/history")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(100L))
                    .andExpect(jsonPath("$.data[0].version").value(1))
                    .andExpect(jsonPath("$.data[0].content").value("Old version content"));
        }
    }

    @Nested
    @DisplayName("POST /system-prompts/{id}/rollback/{historyId} Tests")
    class RollbackToVersionTests {

        @Test
        @DisplayName("Should rollback prompt to specific version and return 200 OK")
        void rollbackToVersion_Success() throws Exception {
            SystemPromptResponse response = buildPromptResponse(1L, "PROMPT_GEN_1", "Question Generator Prompt");
            response.setCurrentContent("Rolled back content");

            when(systemPromptService.rollbackToVersion(eq(1L), eq(100L), any(), any()))
                    .thenReturn(response);

            mockMvc.perform(post("/system-prompts/1/rollback/100")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.currentContent").value("Rolled back content"));
        }
    }

    @Nested
    @DisplayName("POST /system-prompts/test-execute Tests")
    class TestExecuteTests {

        @Test
        @DisplayName("Should test execute prompt and return AI result")
        void testExecute_Success() throws Exception {
            PromptTestExecuteRequest request = PromptTestExecuteRequest.builder()
                    .promptCode("PROMPT_GEN_1")
                    .taskCode("QUESTION_GEN")
                    .customContent("Generate math test for {{topic}}")
                    .variables(Map.of("topic", "Algebra"))
                    .build();

            PromptTestExecuteResponse response = PromptTestExecuteResponse.builder()
                    .promptCode("PROMPT_GEN_1")
                    .taskCode("QUESTION_GEN")
                    .renderedPrompt("Generate math test for Algebra")
                    .aiResponse("Here is a question on Algebra...")
                    .executionTimeMs(450L)
                    .providerCode("GEMINI")
                    .modelName("gemini-1.5-flash")
                    .completionTokens(80)
                    .success(true)
                    .build();

            when(systemPromptService.testExecutePrompt(any(PromptTestExecuteRequest.class), any()))
                    .thenReturn(response);

            mockMvc.perform(post("/system-prompts/test-execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.aiResponse").value("Here is a question on Algebra..."))
                    .andExpect(jsonPath("$.providerCode").value("GEMINI"))
                    .andExpect(jsonPath("$.executionTimeMs").value(450));
        }
    }
}
