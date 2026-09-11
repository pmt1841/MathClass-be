package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
import com.codegym.mathclass.security.services.CustomUserDetails;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssignmentLibraryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private AssignmentSheetService assignmentSheetService;

    @InjectMocks
    private AssignmentLibraryController assignmentLibraryController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@mathclass.edu.vn", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(assignmentLibraryController)
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
    @DisplayName("GET /library/assignments Tests")
    class GetPublicAssignmentsTests {

        @Test
        @DisplayName("Should return paginated public assignments")
        void getPublicAssignments_Success() throws Exception {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Public Math Assignment");

            Page<AssignmentResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);
            when(assignmentService.getPublicAssignments(eq("Math"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/library/assignments")
                            .param("keyword", "Math")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10L))
                    .andExpect(jsonPath("$.content[0].title").value("Public Math Assignment"));
        }
    }

    @Nested
    @DisplayName("POST /library/assignments/{id}/clone Tests")
    class CloneAssignmentTests {

        @Test
        @DisplayName("Should clone assignment from library and return 201 Created")
        void cloneAssignmentFromLibrary_Success() throws Exception {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(20L);
            response.setTitle("Cloned Assignment");

            when(assignmentService.cloneAssignmentFromLibrary(eq(10L), eq(1L))).thenReturn(response);

            mockMvc.perform(post("/library/assignments/10/clone")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(20L))
                    .andExpect(jsonPath("$.title").value("Cloned Assignment"));
        }
    }

    @Nested
    @DisplayName("GET /library/assignment-sheets Tests")
    class GetPublicAssignmentSheetsTests {

        @Test
        @DisplayName("Should return paginated public assignment sheets")
        void getPublicAssignmentSheets_Success() throws Exception {
            AssignmentSheetResponse response = new AssignmentSheetResponse();
            response.setId(30L);
            response.setTitle("Public Sheet 1");

            Page<AssignmentSheetResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);
            when(assignmentSheetService.getPublicAssignmentSheets(eq("Sheet"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/library/assignment-sheets")
                            .param("keyword", "Sheet")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(30L))
                    .andExpect(jsonPath("$.content[0].title").value("Public Sheet 1"));
        }
    }

    @Nested
    @DisplayName("POST /library/assignment-sheets/{id}/clone Tests")
    class CloneAssignmentSheetTests {

        @Test
        @DisplayName("Should clone assignment sheet from library and return 201 Created")
        void cloneAssignmentSheetFromLibrary_Success() throws Exception {
            AssignmentSheetResponse response = new AssignmentSheetResponse();
            response.setId(40L);
            response.setTitle("Cloned Sheet");

            when(assignmentSheetService.cloneAssignmentSheetFromLibrary(eq(30L), eq(1L))).thenReturn(response);

            mockMvc.perform(post("/library/assignment-sheets/30/clone")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(40L))
                    .andExpect(jsonPath("$.title").value("Cloned Sheet"));
        }
    }
}
