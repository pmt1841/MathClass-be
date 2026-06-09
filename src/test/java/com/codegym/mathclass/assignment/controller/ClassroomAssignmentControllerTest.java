package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClassroomAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClassroomAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssignmentService assignmentService;

    private CustomUserDetails studentDetails;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"));
        studentDetails = new CustomUserDetails(2L, "Student", "student@gmail.com", "password", true, authorities);
        authPrincipal = new UsernamePasswordAuthenticationToken(studentDetails, null, studentDetails.getAuthorities());
    }

    @Test
    @DisplayName("Should get classroom assignments successfully")
    void getClassroomAssignments_Success() throws Exception {
        AssignmentResponse response = new AssignmentResponse();
        response.setId(20L);
        response.setTitle("Classroom Assignment");
        Page<AssignmentResponse> page = new PageImpl<>(Collections.singletonList(response));

        when(assignmentService.getAssignmentsByClassCode(eq("MATH101"), eq(2L), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/classrooms/MATH101/assignments")
                .with(authentication(authPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(20L))
                .andExpect(jsonPath("$.content[0].title").value("Classroom Assignment"));
    }

    @Test
    @DisplayName("Should get assignment detail successfully")
    void getAssignmentDetail_Success() throws Exception {
        AssignmentResponse response = new AssignmentResponse();
        response.setId(20L);
        response.setClassCode("MATH101");
        response.setTitle("Detail Assignment");

        when(assignmentService.getAssignmentById(20L, 2L, "STUDENT")).thenReturn(response);

        mockMvc.perform(get("/api/classrooms/MATH101/assignments/20/detail")
                .with(authentication(authPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20L))
                .andExpect(jsonPath("$.classCode").value("MATH101"));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException if assignment does not belong to the class")
    void getAssignmentDetail_AccessDenied() throws Exception {
        AssignmentResponse response = new AssignmentResponse();
        response.setId(20L);
        response.setClassCode("OTHER_CLASS"); // Different class code

        when(assignmentService.getAssignmentById(20L, 2L, "STUDENT")).thenReturn(response);

        mockMvc.perform(get("/api/classrooms/MATH101/assignments/20/detail")
                .with(authentication(authPrincipal)))
                // Note: Exception is handled by global exception handler or throws 500 without
                // it.
                // Since this is a slice test without ControllerAdvice, it may throw nested
                // exception.
                // We'll check if it fails with 403 or 500, normally we should use
                // @ControllerAdvice
                // but let's test for the exception itself.
                .andExpect(status().isForbidden()) // If global handler converts AccessDenied to 403
        // Or if not registered, we can just let it throw or add try-catch in mockMvc
        ;
    }
}
