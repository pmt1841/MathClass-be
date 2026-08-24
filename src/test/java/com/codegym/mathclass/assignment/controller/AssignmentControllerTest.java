package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssignmentService assignmentService;

    @InjectMocks
    private AssignmentController assignmentController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(assignmentController)
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
    @DisplayName("POST /assignments Integration Tests")
    class CreateAssignmentEndpointTests {

        @Test
        @DisplayName("Should create assignment successfully")
        void createAssignment_ValidRequest_ReturnsCreated() throws Exception {
            CreateAssignmentRequest request = new CreateAssignmentRequest();
            request.setTitle("Math Assignment");

            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Math Assignment");

            when(assignmentService.createAssignment(any(CreateAssignmentRequest.class), eq(1L))).thenReturn(response);

            mockMvc.perform(post("/assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.title").value("Math Assignment"));

            verify(assignmentService, times(1)).createAssignment(any(CreateAssignmentRequest.class), eq(1L));
        }
    }

    @Nested
    @DisplayName("PUT /assignments/{id}/publish Integration Tests")
    class PublishAssignmentEndpointTests {

        @Test
        @DisplayName("Should publish assignment successfully")
        void publishAssignment_ValidRequest_ReturnsOk() throws Exception {
            PublishAssignmentRequest request = new PublishAssignmentRequest();
            PublishAssignmentRequest.TargetClass target = new PublishAssignmentRequest.TargetClass("MATH101", LocalDateTime.now().plusDays(7));
            request.setTargets(Collections.singletonList(target));

            doNothing().when(assignmentService).publishAssignment(eq(10L), any(PublishAssignmentRequest.class), eq(1L));

            String requestJson = "{\"targets\":[{\"classCode\":\"MATH101\",\"deadline\":\"2099-12-31T23:59:59\"}]}";

            mockMvc.perform(put("/assignments/10/publish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            verify(assignmentService, times(1)).publishAssignment(eq(10L), any(PublishAssignmentRequest.class), eq(1L));
        }
    }

    @Nested
    @DisplayName("DELETE /assignments/{id} Integration Tests")
    class DeleteAssignmentEndpointTests {

        @Test
        @DisplayName("Should delete assignment successfully")
        void deleteAssignment_ValidId_ReturnsNoContent() throws Exception {
            doNothing().when(assignmentService).deleteAssignment(eq(10L), eq(1L));

            mockMvc.perform(delete("/assignments/10"))
                    .andExpect(status().isNoContent());

            verify(assignmentService, times(1)).deleteAssignment(eq(10L), eq(1L));
        }
    }

    @Nested
    @DisplayName("GET /assignments Integration Tests")
    class GetAllAssignmentsEndpointTests {

        @Test
        @DisplayName("Should return list of assignments")
        void getAllAssignmentsForCurrentUser_ValidRequest_ReturnsOkAndPage() throws Exception {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Math Assignment");

            Page<AssignmentResponse> page = new PageImpl<>(Collections.singletonList(response), PageRequest.of(0, 10), 1);

            when(assignmentService.getAssignmentsForCurrentUser(eq(1L), eq("TEACHER"), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/assignments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10L))
                    .andExpect(jsonPath("$.content[0].title").value("Math Assignment"));

            verify(assignmentService, times(1)).getAssignmentsForCurrentUser(eq(1L), eq("TEACHER"), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /assignments/{id} Integration Tests")
    class GetAssignmentByIdEndpointTests {

        @Test
        @DisplayName("Should return assignment by id")
        void getAssignmentById_ValidId_ReturnsOk() throws Exception {
            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Math Assignment");

            when(assignmentService.getAssignmentById(eq(10L), eq(1L), eq("TEACHER"))).thenReturn(response);

            mockMvc.perform(get("/assignments/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.title").value("Math Assignment"));

            verify(assignmentService, times(1)).getAssignmentById(eq(10L), eq(1L), eq("TEACHER"));
        }
    }

    @Nested
    @DisplayName("PUT /assignments/{id} Integration Tests")
    class UpdateAssignmentEndpointTests {

        @Test
        @DisplayName("Should update assignment successfully")
        void updateAssignment_ValidRequest_ReturnsOk() throws Exception {
            UpdateAssignmentRequest request = new UpdateAssignmentRequest();
            request.setTitle("Updated Title");

            AssignmentResponse response = new AssignmentResponse();
            response.setId(10L);
            response.setTitle("Updated Title");

            when(assignmentService.updateAssignment(eq(10L), any(UpdateAssignmentRequest.class), eq(1L))).thenReturn(response);

            mockMvc.perform(put("/assignments/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.title").value("Updated Title"));

            verify(assignmentService, times(1)).updateAssignment(eq(10L), any(UpdateAssignmentRequest.class), eq(1L));
        }
    }

    @Nested
    @DisplayName("POST /assignments/images Integration Tests")
    class UploadImageEndpointTests {

        @Test
        @DisplayName("Should upload image successfully")
        void uploadImage_ValidFile_ReturnsOkAndDto() throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "content".getBytes());
            AssignmentImageDto response = new AssignmentImageDto("code123", "url");

            when(assignmentService.uploadImageForAssignment(any(MultipartFile.class))).thenReturn(response);

            mockMvc.perform(multipart("/assignments/images")
                            .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageCode").value("code123"))
                    .andExpect(jsonPath("$.imageUrl").value("url"));

            verify(assignmentService, times(1)).uploadImageForAssignment(any(MultipartFile.class));
        }
    }
}
