package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClassroomAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssignmentService assignmentService;

    @InjectMocks
    private ClassroomAssignmentController classroomAssignmentController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                2L, "Student", "student@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(classroomAssignmentController)
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
    @DisplayName("GET /api/classrooms/{classCode}/assignments Integration Tests")
    class GetClassroomAssignmentsEndpointTests {

        @Test
        @DisplayName("Should return list of assignments for a classroom")
        void getClassroomAssignments_ValidRequest_ReturnsOkAndPage() throws Exception {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Math Assignment");

            Page<AssignmentResponse> page = new PageImpl<>(Collections.singletonList(response), PageRequest.of(0, 10), 1);

            when(assignmentService.getAssignmentsByClassCode(eq("MATH101"), eq(2L), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/classrooms/MATH101/assignments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10L))
                    .andExpect(jsonPath("$.content[0].title").value("Math Assignment"));

            verify(assignmentService, times(1)).getAssignmentsByClassCode(eq("MATH101"), eq(2L), any(), any(), any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /classrooms/{classCode}/assignments/{id} Integration Tests")
    class GetAssignmentDetailEndpointTests {

        @Test
        @DisplayName("Should return assignment detail")
        void getAssignmentDetail_ValidRequest_ReturnsOk() throws Exception {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Math Assignment");
            response.setClassCode("MATH101");

            when(assignmentService.getAssignmentById(eq(10L), eq(2L), eq("STUDENT"))).thenReturn(response);

            mockMvc.perform(get("/classrooms/MATH101/assignments/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.title").value("Math Assignment"));

            verify(assignmentService, times(1)).getAssignmentById(eq(10L), eq(2L), eq("STUDENT"));
        }
    }
}
