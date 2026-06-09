package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.AddStudentRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.service.ClassroomService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ClassroomMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClassroomMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassroomService classroomService;

    @Autowired
    private ObjectMapper objectMapper;

    private CustomUserDetails teacherDetails;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"));
        teacherDetails = new CustomUserDetails(1L, "Teacher", "teacher@gmail.com", "password", true, authorities);
        authPrincipal = new UsernamePasswordAuthenticationToken(teacherDetails, null, teacherDetails.getAuthorities());
    }

    @Test
    @DisplayName("Should add student to class successfully")
    void addStudentToClass_Success() throws Exception {
        AddStudentRequest request = new AddStudentRequest();
        request.setStudentEmail("student@gmail.com");

        doNothing().when(classroomService).addStudentToClass("MATH101", "student@gmail.com", 1L);

        mockMvc.perform(post("/api/classrooms/MATH101/students/add")
                        .with(authentication(authPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(classroomService, times(1)).addStudentToClass("MATH101", "student@gmail.com", 1L);
    }

    @Test
    @DisplayName("Should return 400 Bad Request if add student request is invalid (missing email)")
    void addStudentToClass_InvalidRequest() throws Exception {
        AddStudentRequest request = new AddStudentRequest();
        request.setStudentEmail(null); // Invalid: email cannot be null

        mockMvc.perform(post("/api/classrooms/MATH101/students/add")
                        .with(authentication(authPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get students by class code successfully")
    void getStudentsByClassCode_Success() throws Exception {
        StudentResponse response = new StudentResponse();
        response.setId(2L);
        response.setEmail("student@gmail.com");
        Page<StudentResponse> page = new PageImpl<>(Collections.singletonList(response));

        when(classroomService.getStudentsByClassCode(eq("MATH101"), eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/classrooms/MATH101/students")
                        .with(authentication(authPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2L))
                .andExpect(jsonPath("$.content[0].email").value("student@gmail.com"));
    }

    @Test
    @DisplayName("Should remove student from class successfully")
    void removeStudentFromClass_Success() throws Exception {
        doNothing().when(classroomService).removeStudentFromClass("MATH101", 2L, 1L);

        mockMvc.perform(delete("/api/classrooms/MATH101/students/2")
                        .with(authentication(authPrincipal)))
                .andExpect(status().isOk());

        verify(classroomService, times(1)).removeStudentFromClass("MATH101", 2L, 1L);
    }
}
