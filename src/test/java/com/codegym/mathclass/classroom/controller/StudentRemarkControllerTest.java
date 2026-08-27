package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.CreateStudentRemarkRequest;
import com.codegym.mathclass.classroom.dto.StudentRemarkResponse;
import com.codegym.mathclass.classroom.service.StudentRemarkService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StudentRemarkControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentRemarkService studentRemarkService;

    @InjectMocks
    private StudentRemarkController studentRemarkController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@mathclass.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("classroom:manage_requests"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(studentRemarkController)
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

    @Nested
    @DisplayName("GET /classrooms/{classCode}/students/{studentId}/remarks Tests")
    class GetRemarksTests {

        @Test
        @DisplayName("Should return list of remarks and status 200 OK")
        void getStudentRemarks_ReturnsList() throws Exception {
            StudentRemarkResponse response = StudentRemarkResponse.builder()
                    .id(1L)
                    .studentId(2L)
                    .studentName("Học sinh Lê Thị Bình")
                    .teacherId(1L)
                    .teacherName("Thầy Nguyễn Văn A")
                    .strengths("Tư duy tốt")
                    .weaknesses("Tính ẩu")
                    .generalAssessment("Khá")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(studentRemarkService.getStudentRemarks(eq("MATH101"), eq(2L), eq(1L)))
                    .thenReturn(List.of(response));

            mockMvc.perform(get("/classrooms/MATH101/students/2/remarks")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1L))
                    .andExpect(jsonPath("$[0].strengths").value("Tư duy tốt"))
                    .andExpect(jsonPath("$[0].studentName").value("Học sinh Lê Thị Bình"));

            verify(studentRemarkService).getStudentRemarks("MATH101", 2L, 1L);
        }
    }

    @Nested
    @DisplayName("POST /classrooms/{classCode}/students/{studentId}/remarks Tests")
    class CreateRemarkTests {

        @Test
        @DisplayName("Should create remark and return status 201 Created")
        void createStudentRemark_ReturnsCreated() throws Exception {
            CreateStudentRemarkRequest request = new CreateStudentRemarkRequest();
            request.setStrengths("Chăm chỉ");
            request.setWeaknesses("Chưa tự tin");
            request.setGeneralAssessment("Cần phát biểu nhiều hơn");

            StudentRemarkResponse response = StudentRemarkResponse.builder()
                    .id(10L)
                    .studentId(2L)
                    .studentName("Học sinh Lê Thị Bình")
                    .teacherId(1L)
                    .teacherName("Thầy Nguyễn Văn A")
                    .strengths("Chăm chỉ")
                    .weaknesses("Chưa tự tin")
                    .generalAssessment("Cần phát biểu nhiều hơn")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(studentRemarkService.createStudentRemark(eq("MATH101"), eq(2L), eq(1L), any(CreateStudentRemarkRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/classrooms/MATH101/students/2/remarks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.strengths").value("Chăm chỉ"));

            verify(studentRemarkService).createStudentRemark(eq("MATH101"), eq(2L), eq(1L), any(CreateStudentRemarkRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /classrooms/{classCode}/students/{studentId}/remarks/{remarkId} Tests")
    class DeleteRemarkTests {

        @Test
        @DisplayName("Should delete remark and return status 204 No Content")
        void deleteStudentRemark_ReturnsNoContent() throws Exception {
            doNothing().when(studentRemarkService).deleteStudentRemark("MATH101", 2L, 5L, 1L);

            mockMvc.perform(delete("/classrooms/MATH101/students/2/remarks/5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(studentRemarkService).deleteStudentRemark("MATH101", 2L, 5L, 1L);
        }
    }
}
