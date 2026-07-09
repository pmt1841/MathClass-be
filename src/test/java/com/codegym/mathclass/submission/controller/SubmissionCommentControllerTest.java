package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.SubmissionCommentRequest;
import com.codegym.mathclass.submission.dto.SubmissionCommentResponse;
import com.codegym.mathclass.submission.service.SubmissionCommentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionCommentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubmissionCommentService submissionCommentService;

    @InjectMocks
    private SubmissionCommentController submissionCommentController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(submissionCommentController)
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

    // ==========================================
    // Tests for getCommentsBySubmissionId
    // ==========================================

    @Test
    @DisplayName("Should get comments by submission id successfully")
    void getCommentsBySubmissionId_ValidId_ReturnsOk() throws Exception {
        // Given
        SubmissionCommentResponse response = new SubmissionCommentResponse();
        response.setId(10L);
        response.setContent("Good job");

        List<SubmissionCommentResponse> responses = Collections.singletonList(response);

        when(submissionCommentService.getCommentsBySubmissionId(100L)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/submissions/100/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].content").value("Good job"));

        verify(submissionCommentService, times(1)).getCommentsBySubmissionId(100L);
    }

    // ==========================================
    // Tests for addComment
    // ==========================================

    @Test
    @DisplayName("Should add comment successfully")
    void addComment_ValidRequest_ReturnsCreated() throws Exception {
        // Given
        SubmissionCommentRequest request = new SubmissionCommentRequest();
        request.setContent("Needs improvement");

        SubmissionCommentResponse response = new SubmissionCommentResponse();
        response.setId(10L);
        response.setContent("Needs improvement");

        when(submissionCommentService.addComment(eq(100L), eq(1L), any(SubmissionCommentRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/submissions/100/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.content").value("Needs improvement"));

        verify(submissionCommentService, times(1)).addComment(eq(100L), eq(1L), any(SubmissionCommentRequest.class));
    }

    // ==========================================
    // Tests for deleteComment
    // ==========================================

    @Test
    @DisplayName("Should delete comment successfully")
    void deleteComment_ValidIds_ReturnsNoContent() throws Exception {
        // Given
        doNothing().when(submissionCommentService).deleteComment(100L, 10L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/submissions/100/comments/10"))
                .andExpect(status().isNoContent());

        verify(submissionCommentService, times(1)).deleteComment(100L, 10L, 1L);
    }
}
