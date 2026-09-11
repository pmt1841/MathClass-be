package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalPromptControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PromptRenderService promptRenderService;

    @InjectMocks
    private InternalPromptController internalPromptController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(internalPromptController).build();
    }

    @Test
    @DisplayName("POST /system-prompts/render - Render prompt thành công")
    void testRenderPrompt_Success() throws Exception {
        RenderPromptRequest request = RenderPromptRequest.builder()
                .promptCode("PROMPT_MATH_HINT")
                .variables(Map.of("step", 1, "topic", "Geometry"))
                .build();

        RenderPromptResponse response = RenderPromptResponse.builder()
                .promptCode("PROMPT_MATH_HINT")
                .renderedPrompt("Gợi ý bước 1 cho chủ đề Geometry: ...")
                .usedVariables(List.of("step", "topic"))
                .build();

        when(promptRenderService.renderPrompt(any(RenderPromptRequest.class))).thenReturn(response);

        mockMvc.perform(post("/system-prompts/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptCode").value("PROMPT_MATH_HINT"))
                .andExpect(jsonPath("$.renderedPrompt").value("Gợi ý bước 1 cho chủ đề Geometry: ..."))
                .andExpect(jsonPath("$.usedVariables[0]").value("step"))
                .andExpect(jsonPath("$.usedVariables[1]").value("topic"));
    }
}
