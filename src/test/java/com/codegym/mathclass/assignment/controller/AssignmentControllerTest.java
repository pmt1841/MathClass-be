package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.context.annotation.Import;

@WebMvcTest(controllers = AssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.codegym.mathclass.config.TestSecurityConfig.class)
class AssignmentControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AssignmentService assignmentService;

        private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        private CustomUserDetails teacherDetails;
        private UsernamePasswordAuthenticationToken authPrincipal;

        @BeforeEach
        void setUp() {
                List<GrantedAuthority> authorities = Collections
                                .singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"));
                teacherDetails = new CustomUserDetails(1L, "Teacher", "teacher@gmail.com", "password", true,
                                authorities);
                authPrincipal = new UsernamePasswordAuthenticationToken(teacherDetails, null,
                                teacherDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authPrincipal);
        }

        @Test
        @DisplayName("Should create assignment successfully")
        void createAssignment_Success() throws Exception {
                CreateAssignmentRequest request = new CreateAssignmentRequest();
                request.setTitle("New Assignment");
                request.setDescription("Assignment description");
                request.setContent("Content");

                AssignmentResponse response = new AssignmentResponse();
                response.setId(10L);
                response.setTitle("New Assignment");
                response.setStatus(AssignmentStatus.DRAFT);

                when(assignmentService.createAssignment(any(CreateAssignmentRequest.class), eq(1L)))
                                .thenReturn(response);

                mockMvc.perform(post("/api/assignments/create")
                                .with(authentication(authPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(10L))
                                .andExpect(jsonPath("$.title").value("New Assignment"))
                                .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("Should publish assignment successfully")
        void publishAssignment_Success() throws Exception {
                PublishAssignmentRequest request = new PublishAssignmentRequest();
                request.setTargets(Collections.singletonList(new PublishAssignmentRequest.TargetClass("MATH101", java.time.LocalDateTime.now().plusDays(1))));

                doNothing().when(assignmentService).publishAssignment(eq(10L), any(PublishAssignmentRequest.class),
                                eq(1L));

                String jsonBody = "{\"targets\": [{\"classCode\":\"MATH101\", \"deadline\":\"2026-12-31T23:59:59\"}]}";

                mockMvc.perform(put("/api/assignments/10/publish")
                                .with(authentication(authPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody))
                                .andExpect(status().isOk());

                verify(assignmentService, times(1)).publishAssignment(eq(10L), any(PublishAssignmentRequest.class),
                                eq(1L));
        }

        @Test
        @DisplayName("Should delete assignment successfully")
        void deleteAssignment_Success() throws Exception {
                doNothing().when(assignmentService).deleteAssignment(eq(10L), eq(1L));

                mockMvc.perform(delete("/api/assignments/10")
                                .with(authentication(authPrincipal)))
                                .andExpect(status().isOk());

                verify(assignmentService, times(1)).deleteAssignment(10L, 1L);
        }

        @Test
        @DisplayName("Should get all assignments for current user")
        void getAllAssignments_Success() throws Exception {
                AssignmentResponse response = new AssignmentResponse();
                response.setId(10L);
                response.setTitle("Assignment 1");
                Page<AssignmentResponse> page = new PageImpl<>(Collections.singletonList(response));

                when(assignmentService.getAssignmentsForCurrentUser(eq(1L), eq("TEACHER"), any(), any(), any(),
                                any(Pageable.class)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/assignments")
                                .with(authentication(authPrincipal))
                                .param("keyword", "test"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].id").value(10L));
        }

        @Test
        @DisplayName("Should get assignment by id")
        void getAssignmentById_Success() throws Exception {
                AssignmentResponse response = new AssignmentResponse();
                response.setId(10L);
                response.setTitle("Assignment 1");

                when(assignmentService.getAssignmentById(10L, 1L, "TEACHER")).thenReturn(response);

                mockMvc.perform(get("/api/assignments/10")
                                .with(authentication(authPrincipal)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(10L))
                                .andExpect(jsonPath("$.title").value("Assignment 1"));
        }

        @Test
        @DisplayName("Should update assignment successfully")
        void updateAssignment_Success() throws Exception {
                UpdateAssignmentRequest request = new UpdateAssignmentRequest();
                request.setTitle("Updated Title");
                request.setDescription("Description");
                request.setContent("Content");

                AssignmentResponse response = new AssignmentResponse();
                response.setId(10L);
                response.setTitle("Updated Title");

                when(assignmentService.updateAssignment(eq(10L), any(UpdateAssignmentRequest.class), eq(1L)))
                                .thenReturn(response);

                mockMvc.perform(put("/api/assignments/10")
                                .with(authentication(authPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(10L))
                                .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        @DisplayName("Should handle IllegalArgumentException for LaTeX validation")
        void createAssignment_BadRequest_LaTeX() throws Exception {
                CreateAssignmentRequest request = new CreateAssignmentRequest();
                request.setTitle("New Assignment");
                request.setDescription("Description");
                request.setContent("Invalid \\input");

                when(assignmentService.createAssignment(any(CreateAssignmentRequest.class), eq(1L)))
                                .thenThrow(new IllegalArgumentException("Invalid LaTeX"));

                mockMvc.perform(post("/api/assignments/create")
                                .with(authentication(authPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string("Invalid LaTeX"));
        }
}
