package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.service.SubmissionDrawingService;
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
import org.springframework.security.core.userdetails.UserDetails;
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
class SubmissionDrawingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubmissionDrawingService submissionDrawingService;

    @InjectMocks
    private SubmissionDrawingController submissionDrawingController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "student@gmail.com", "student@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(submissionDrawingController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(UserDetails.class);
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
    // Tests for saveOrUpdateDrawing
    // ==========================================

    @Test
    @DisplayName("Should save or update drawing successfully")
    void saveOrUpdateDrawing_ValidRequest_ReturnsOk() throws Exception {
        // Given
        SubmissionDrawingRequest request = new SubmissionDrawingRequest();
        request.setShapeCode("data:image/png;base64,...");
        request.setJsxGraphData(java.util.Collections.emptyMap());

        SubmissionDrawingResponse response = new SubmissionDrawingResponse();
        response.setId(10L);
        response.setShapeCode("data:image/png;base64,...");

        when(submissionDrawingService.saveOrUpdateDrawing(eq(100L), any(SubmissionDrawingRequest.class), eq("student@gmail.com")))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/submissions/100/drawings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Drawing saved successfully"))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.shapeCode").value("data:image/png;base64,..."));

        verify(submissionDrawingService, times(1)).saveOrUpdateDrawing(eq(100L), any(SubmissionDrawingRequest.class), eq("student@gmail.com"));
    }

    // ==========================================
    // Tests for getDrawing
    // ==========================================

    @Test
    @DisplayName("Should get drawing successfully")
    void getDrawing_ValidId_ReturnsOk() throws Exception {
        // Given
        SubmissionDrawingResponse response = new SubmissionDrawingResponse();
        response.setId(10L);
        response.setShapeCode("data:image/png;base64,...");

        when(submissionDrawingService.getDrawingBySubmissionId(100L, "student@gmail.com")).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/submissions/100/drawings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.shapeCode").value("data:image/png;base64,..."));

        verify(submissionDrawingService, times(1)).getDrawingBySubmissionId(100L, "student@gmail.com");
    }
}
