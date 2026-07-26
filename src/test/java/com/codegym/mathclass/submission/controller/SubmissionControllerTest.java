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
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("POST /api/submissions Integration Tests")
    class CreateSubmissionEndpointTests {

        @Test
        @DisplayName("Should create submission successfully and return 200 OK")
        void createSubmission_ValidRequest_ReturnsOk() throws Exception {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(100L);

            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);
            response.setStatus(SubmissionStatus.SUBMITTED);

            when(submissionService.createSubmission(eq(1L), any(SubmissionRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/submissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.status").value(SubmissionStatus.SUBMITTED.toString()));

            verify(submissionService, times(1)).createSubmission(eq(1L), any(SubmissionRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/submissions/{submissionId} Integration Tests")
    class UpdateSubmissionEndpointTests {

        @Test
        @DisplayName("Should update submission successfully and return 200 OK")
        void updateSubmission_ValidRequest_ReturnsOk() throws Exception {
            SubmissionRequest request = new SubmissionRequest();
            request.setAssignmentId(100L);

            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);
            response.setStatus(SubmissionStatus.SUBMITTED);

            when(submissionService.updateSubmission(eq(10L), eq(1L), any(SubmissionRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/submissions/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L));

            verify(submissionService, times(1)).updateSubmission(eq(10L), eq(1L), any(SubmissionRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /api/submissions/{submissionId}/unsubmit Integration Tests")
    class UnsubmitSubmissionEndpointTests {

        @Test
        @DisplayName("Should unsubmit submission successfully and return 200 OK")
        void unsubmitSubmission_ValidId_ReturnsOk() throws Exception {
            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);
            response.setStatus(SubmissionStatus.DRAFT);

            when(submissionService.unsubmitSubmission(10L, 1L)).thenReturn(response);

            mockMvc.perform(put("/api/submissions/10/unsubmit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(SubmissionStatus.DRAFT.toString()));

            verify(submissionService, times(1)).unsubmitSubmission(10L, 1L);
        }
    }

    @Nested
    @DisplayName("PUT /api/submissions/{submissionId}/grade Integration Tests")
    class GradeSubmissionEndpointTests {

        @Test
        @DisplayName("Should grade submission successfully and return 200 OK")
        void gradeSubmission_ValidRequest_ReturnsOk() throws Exception {
            GradeRequest request = new GradeRequest();
            request.setScore(9.5);

            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);
            response.setScore(9.5);
            response.setStatus(SubmissionStatus.GRADED);

            when(submissionService.gradeSubmission(eq(10L), eq(1L), any(GradeRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/submissions/10/grade")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(9.5))
                    .andExpect(jsonPath("$.status").value(SubmissionStatus.GRADED.toString()));

            verify(submissionService, times(1)).gradeSubmission(eq(10L), eq(1L), any(GradeRequest.class));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when score is negative")
        void gradeSubmission_NegativeScore_Returns400BadRequest() throws Exception {
            GradeRequest request = new GradeRequest();
            request.setScore(-1.0);

            mockMvc.perform(put("/api/submissions/10/grade")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(submissionService, never()).gradeSubmission(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when score exceeds 10")
        void gradeSubmission_ExceedsMaxScore_Returns400BadRequest() throws Exception {
            GradeRequest request = new GradeRequest();
            request.setScore(11.0);

            mockMvc.perform(put("/api/submissions/10/grade")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(submissionService, never()).gradeSubmission(anyLong(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/submissions/my-submission Integration Tests")
    class GetMySubmissionEndpointTests {

        @Test
        @DisplayName("Should get my submission successfully and return 200 OK")
        void getMySubmission_ValidId_ReturnsOk() throws Exception {
            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);

            when(submissionService.getMySubmission(100L, 1L)).thenReturn(response);

            mockMvc.perform(get("/api/submissions/my-submission").param("assignmentId", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L));

            verify(submissionService, times(1)).getMySubmission(100L, 1L);
        }

        @Test
        @DisplayName("Should return 204 No Content when my submission is not found")
        void getMySubmission_NotFound_ReturnsNoContent() throws Exception {
            when(submissionService.getMySubmission(100L, 1L)).thenReturn(null);

            mockMvc.perform(get("/api/submissions/my-submission").param("assignmentId", "100"))
                    .andExpect(status().isNoContent());

            verify(submissionService, times(1)).getMySubmission(100L, 1L);
        }
    }

    @Nested
    @DisplayName("GET /api/submissions Integration Tests")
    class GetSubmissionsByAssignmentEndpointTests {

        @Test
        @DisplayName("Should get submissions by assignment and return 200 OK")
        void getSubmissionsByAssignment_ValidRequest_ReturnsOkAndPage() throws Exception {
            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);

            Page<SubmissionResponse> page = new PageImpl<>(Collections.singletonList(response), PageRequest.of(0, 10), 1);

            when(submissionService.getSubmissionsByAssignment(eq(100L), eq(1L), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/submissions")
                            .param("assignmentId", "100")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10L));

            verify(submissionService, times(1)).getSubmissionsByAssignment(eq(100L), eq(1L), any(), any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /api/submissions/{submissionId} Integration Tests")
    class GetSubmissionDetailEndpointTests {

        @Test
        @DisplayName("Should get submission detail and return 200 OK")
        void getSubmissionDetail_ValidId_ReturnsOk() throws Exception {
            SubmissionResponse response = new SubmissionResponse();
            response.setId(10L);

            when(submissionService.getSubmissionDetail(10L, 1L)).thenReturn(response);

            mockMvc.perform(get("/api/submissions/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L));

            verify(submissionService, times(1)).getSubmissionDetail(10L, 1L);
        }
    }
}
