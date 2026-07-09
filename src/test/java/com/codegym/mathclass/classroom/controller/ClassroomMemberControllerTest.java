package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.AddStudentRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.service.ClassroomService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
class ClassroomMemberControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassroomService classroomService;

    @InjectMocks
    private ClassroomMemberController classroomMemberController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(classroomMemberController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
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

    // ==========================================
    // Tests for addStudentToClass
    // ==========================================

    @Test
    @DisplayName("Should add student to classroom successfully")
    void addStudentToClass_ValidRequest_ReturnsOk() throws Exception {
        // Given
        AddStudentRequest request = new AddStudentRequest();
        request.setStudentEmail("student@gmail.com");

        doNothing().when(classroomService).addStudentToClass(eq("MATH101"), eq("student@gmail.com"), eq(1L));

        // When & Then
        mockMvc.perform(post("/api/classrooms/MATH101/students/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
                
        verify(classroomService, times(1)).addStudentToClass(eq("MATH101"), eq("student@gmail.com"), eq(1L));
    }

    // ==========================================
    // Tests for getStudentsByClassCode
    // ==========================================

    @Test
    @DisplayName("Should get students by class code successfully")
    void getStudentsByClassCode_ValidRequest_ReturnsOkAndPage() throws Exception {
        // Given
        StudentResponse studentResponse = new StudentResponse();
        studentResponse.setId(2L);
        studentResponse.setFullName("Student One");
        studentResponse.setEmail("student@gmail.com");

        Page<StudentResponse> page = new PageImpl<>(Collections.singletonList(studentResponse), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        
        when(classroomService.getStudentsByClassCode(eq("MATH101"), eq(1L), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/classrooms/MATH101/students")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "s.fullName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2L))
                .andExpect(jsonPath("$.content[0].fullName").value("Student One"));
                
        verify(classroomService, times(1)).getStudentsByClassCode(eq("MATH101"), eq(1L), any(Pageable.class));
    }

    // ==========================================
    // Tests for removeStudentFromClass
    // ==========================================

    @Test
    @DisplayName("Should remove student from class successfully")
    void removeStudentFromClass_ValidRequest_ReturnsOk() throws Exception {
        // Given
        doNothing().when(classroomService).removeStudentFromClass(eq("MATH101"), eq(2L), eq(1L));

        // When & Then
        mockMvc.perform(delete("/api/classrooms/MATH101/students/2"))
                .andExpect(status().isOk());
                
        verify(classroomService, times(1)).removeStudentFromClass(eq("MATH101"), eq(2L), eq(1L));
    }
}
