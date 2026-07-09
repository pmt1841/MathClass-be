package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.service.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubmissionService submissionService;

    @InjectMocks
    private SubmissionController submissionController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Student", "student@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(submissionController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new HandlerMethodArgumentResolver() {
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
    // Tests for createSubmission
    // ==========================================

    @Test
    @DisplayName("Should create submission successfully")
    void createSubmission_ValidRequest_ReturnsOk() throws Exception {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(100L);

        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);
        response.setStatus(SubmissionStatus.SUBMITTED);

        when(submissionService.createSubmission(eq(1L), any(SubmissionRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.status").value(SubmissionStatus.SUBMITTED.toString()));

        verify(submissionService, times(1)).createSubmission(eq(1L), any(SubmissionRequest.class));
    }

    // ==========================================
    // Tests for updateSubmission
    // ==========================================

    @Test
    @DisplayName("Should update submission successfully")
    void updateSubmission_ValidRequest_ReturnsOk() throws Exception {
        // Given
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(100L);

        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);
        response.setStatus(SubmissionStatus.SUBMITTED);

        when(submissionService.updateSubmission(eq(10L), eq(1L), any(SubmissionRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/submissions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));

        verify(submissionService, times(1)).updateSubmission(eq(10L), eq(1L), any(SubmissionRequest.class));
    }

    // ==========================================
    // Tests for unsubmitSubmission
    // ==========================================

    @Test
    @DisplayName("Should unsubmit submission successfully")
    void unsubmitSubmission_ValidId_ReturnsOk() throws Exception {
        // Given
        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);
        response.setStatus(SubmissionStatus.DRAFT);

        when(submissionService.unsubmitSubmission(10L, 1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/submissions/10/unsubmit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SubmissionStatus.DRAFT.toString()));

        verify(submissionService, times(1)).unsubmitSubmission(10L, 1L);
    }

    // ==========================================
    // Tests for gradeSubmission
    // ==========================================

    @Test
    @DisplayName("Should grade submission successfully")
    void gradeSubmission_ValidRequest_ReturnsOk() throws Exception {
        // Given
        GradeRequest request = new GradeRequest();
        request.setScore(9.5);

        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);
        response.setScore(9.5);
        response.setStatus(SubmissionStatus.GRADED);

        when(submissionService.gradeSubmission(eq(10L), eq(1L), any(GradeRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/submissions/10/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(9.5))
                .andExpect(jsonPath("$.status").value(SubmissionStatus.GRADED.toString()));

        verify(submissionService, times(1)).gradeSubmission(eq(10L), eq(1L), any(GradeRequest.class));
    }

    // ==========================================
    // Tests for getMySubmission
    // ==========================================

    @Test
    @DisplayName("Should get my submission successfully")
    void getMySubmission_ValidId_ReturnsOk() throws Exception {
        // Given
        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);

        when(submissionService.getMySubmission(100L, 1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/submissions/my-submission").param("assignmentId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));

        verify(submissionService, times(1)).getMySubmission(100L, 1L);
    }

    @Test
    @DisplayName("Should return no content when my submission is not found")
    void getMySubmission_NotFound_ReturnsNoContent() throws Exception {
        // Given
        when(submissionService.getMySubmission(100L, 1L)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/submissions/my-submission").param("assignmentId", "100"))
                .andExpect(status().isNoContent());

        verify(submissionService, times(1)).getMySubmission(100L, 1L);
    }

    // ==========================================
    // Tests for getSubmissionsByAssignment
    // ==========================================

    @Test
    @DisplayName("Should get submissions by assignment")
    void getSubmissionsByAssignment_ValidRequest_ReturnsOkAndPage() throws Exception {
        // Given
        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);

        Page<SubmissionResponse> page = new PageImpl<>(Collections.singletonList(response), PageRequest.of(0, 10), 1);

        when(submissionService.getSubmissionsByAssignment(eq(100L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/submissions")
                        .param("assignmentId", "100")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10L));

        verify(submissionService, times(1)).getSubmissionsByAssignment(eq(100L), eq(1L), any(), any(), any(Pageable.class));
    }

    // ==========================================
    // Tests for getSubmissionDetail
    // ==========================================

    @Test
    @DisplayName("Should get submission detail")
    void getSubmissionDetail_ValidId_ReturnsOk() throws Exception {
        // Given
        SubmissionResponse response = new SubmissionResponse();
        response.setId(10L);

        when(submissionService.getSubmissionDetail(10L, 1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/submissions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));

        verify(submissionService, times(1)).getSubmissionDetail(10L, 1L);
    }
}
