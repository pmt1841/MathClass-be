package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;
import com.codegym.mathclass.classroom.service.ClassroomService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.context.annotation.Import;

@WebMvcTest(controllers = ClassroomController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.codegym.mathclass.config.TestSecurityConfig.class)
class ClassroomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassroomService classroomService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private CustomUserDetails teacherDetails;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"));
        teacherDetails = new CustomUserDetails(1L, "Teacher", "teacher@gmail.com", "password", true, authorities);
        authPrincipal = new UsernamePasswordAuthenticationToken(teacherDetails, null, teacherDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authPrincipal);
    }

    @Test
    @DisplayName("Should create classroom successfully")
    void createClassroom_Success() throws Exception {
        CreateClassroomRequest request = new CreateClassroomRequest();
        request.setName("Math 101");

        ClassroomResponse response = new ClassroomResponse();
        response.setId(100L);
        response.setClassCode("MATH101");
        response.setClassName("Math 101");

        when(classroomService.createClassroom(any(CreateClassroomRequest.class), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/classrooms/create")
                .with(authentication(authPrincipal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.classCode").value("MATH101"))
                .andExpect(jsonPath("$.className").value("Math 101"));
    }

    @Test
    @DisplayName("Should return list of classrooms for user")
    void getClassroomsList_Success() throws Exception {
        ClassroomResponse response = new ClassroomResponse();
        response.setId(100L);
        response.setClassCode("MATH101");

        when(classroomService.getClassroomsListById(1L)).thenReturn(Collections.singletonList(response));

        mockMvc.perform(get("/api/classrooms/my-classroom")
                .with(authentication(authPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].classCode").value("MATH101"));
    }

    @Test
    @DisplayName("Should return classroom by classCode")
    void getClassroomByClassCode_Success() throws Exception {
        ClassroomResponse response = new ClassroomResponse();
        response.setId(100L);
        response.setClassCode("MATH101");

        when(classroomService.getClassroomByClassCode("MATH101", 1L)).thenReturn(response);

        mockMvc.perform(get("/api/classrooms/MATH101")
                .with(authentication(authPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.classCode").value("MATH101"));
    }

    @Test
    @DisplayName("Should update classroom successfully")
    void updateClassroom_Success() throws Exception {
        UpdateClassroomRequest request = new UpdateClassroomRequest();
        request.setClassName("Updated Math");
        request.setMaxStudents(30);

        ClassroomResponse response = new ClassroomResponse();
        response.setId(100L);
        response.setClassCode("MATH101");
        response.setClassName("Updated Math");

        when(classroomService.updateClassroom(eq("MATH101"), any(UpdateClassroomRequest.class), eq(1L)))
                .thenReturn(response);

        mockMvc.perform(put("/api/classrooms/MATH101")
                .with(authentication(authPrincipal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("Updated Math"));
    }
}
