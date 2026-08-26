package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.service.SubmissionDrawingService;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.Map;

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

    @Nested
    @DisplayName("PUT /api/submissions/{submissionId}/drawings Integration Tests")
    class SaveOrUpdateDrawingEndpointTests {

        @Test
        @DisplayName("Should save or update drawing successfully and return 200 OK")
        void saveOrUpdateDrawing_ValidRequest_ReturnsOk() throws Exception {
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();
            request.setShapeCode("TRIANGLE");
            request.setJsxGraphData(Map.of("key", "val"));

            SubmissionDrawingResponse response = new SubmissionDrawingResponse();
            response.setId(10L);
            response.setShapeCode("TRIANGLE");

            when(submissionDrawingService.saveOrUpdateDrawing(eq(100L), any(SubmissionDrawingRequest.class), eq("student@gmail.com")))
                    .thenReturn(response);

            mockMvc.perform(put("/submissions/100/drawings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.shapeCode").value("TRIANGLE"));

            verify(submissionDrawingService, times(1)).saveOrUpdateDrawing(eq(100L), any(SubmissionDrawingRequest.class), eq("student@gmail.com"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when shapeCode is blank")
        void saveOrUpdateDrawing_BlankShapeCode_Returns400BadRequest() throws Exception {
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();
            request.setShapeCode("");
            request.setJsxGraphData(Map.of("key", "val"));

            mockMvc.perform(put("/submissions/100/drawings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(submissionDrawingService, never()).saveOrUpdateDrawing(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when jsxGraphData is null")
        void saveOrUpdateDrawing_NullJsxGraphData_Returns400BadRequest() throws Exception {
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();
            request.setShapeCode("TRIANGLE");
            request.setJsxGraphData(null);

            mockMvc.perform(put("/submissions/100/drawings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(submissionDrawingService, never()).saveOrUpdateDrawing(anyLong(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("GET /submissions/{submissionId}/drawings Integration Tests")
    class GetDrawingEndpointTests {

        @Test
        @DisplayName("Should get drawing successfully and return 200 OK")
        void getDrawing_ValidId_ReturnsOk() throws Exception {
            SubmissionDrawingResponse response = new SubmissionDrawingResponse();
            response.setId(10L);
            response.setShapeCode("TRIANGLE");

            when(submissionDrawingService.getDrawingBySubmissionId(100L, "student@gmail.com")).thenReturn(response);

            mockMvc.perform(get("/submissions/100/drawings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.shapeCode").value("TRIANGLE"));

            verify(submissionDrawingService, times(1)).getDrawingBySubmissionId(100L, "student@gmail.com");
        }
    }
}
