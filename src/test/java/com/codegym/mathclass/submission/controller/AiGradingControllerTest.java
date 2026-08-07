package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.codegym.mathclass.submission.dto.response.DrawingIssueItem;
import com.codegym.mathclass.submission.service.AiGradingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiGradingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiGradingService aiGradingService;

    @InjectMocks
    private AiGradingController aiGradingController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockTeacherDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockTeacherDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(aiGradingController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockTeacherDetails;
                    }
                })
                .build();
    }

    @Nested
    @DisplayName("POST /submissions/{submissionId}/ai-grading Tests (standalone mapping không prefix /api)")
    class RequestAiGradingEndpointTests {

        @Test
        @DisplayName("Should return AI grading draft with 200 OK")
        void requestAiGrading_validRequest_returnsOk() throws Exception {
            AiGradingResponse response = AiGradingResponse.builder()
                    .suggestedScore(8.5)
                    .draftFeedback("Lời giải đúng hướng.")
                    .hasCanvasComparison(true)
                    .drawingIssues(List.of(
                            DrawingIssueItem.builder().issue("Thiếu đường cao AH").detail("Cần kẻ AH").build()
                    ))
                    .build();

            when(aiGradingService.requestAiGrading(eq(100L), any(AiGradingRequest.class), eq(1L)))
                    .thenReturn(response);

            mockMvc.perform(post("/submissions/100/ai-grading")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AiGradingRequest(10L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.suggestedScore").value(8.5))
                    .andExpect(jsonPath("$.draftFeedback").value("Lời giải đúng hướng."))
                    .andExpect(jsonPath("$.hasCanvasComparison").value(true))
                    .andExpect(jsonPath("$.drawingIssues[0].issue").value("Thiếu đường cao AH"));

            verify(aiGradingService).requestAiGrading(eq(100L), any(AiGradingRequest.class), eq(1L));
        }

        @Test
        @DisplayName("Should work when request body is empty")
        void requestAiGrading_emptyBody_returnsOk() throws Exception {
            AiGradingResponse response = AiGradingResponse.builder()
                    .suggestedScore(7.0)
                    .draftFeedback("OK")
                    .hasCanvasComparison(false)
                    .drawingIssues(List.of())
                    .build();

            when(aiGradingService.requestAiGrading(eq(200L), any(AiGradingRequest.class), eq(1L)))
                    .thenReturn(response);

            mockMvc.perform(post("/submissions/200/ai-grading"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.suggestedScore").value(7.0))
                    .andExpect(jsonPath("$.drawingIssues").isEmpty());
        }
    }
}
