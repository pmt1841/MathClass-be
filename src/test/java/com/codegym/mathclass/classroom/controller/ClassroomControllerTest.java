package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;
import com.codegym.mathclass.classroom.service.ClassroomService;
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

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClassroomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassroomService classroomService;

    @InjectMocks
    private ClassroomController classroomController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(classroomController)
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
    @DisplayName("POST /classrooms Integration Tests")
    class CreateClassroomEndpointTests {

        @Test
        @DisplayName("Should create classroom successfully and return 201 Created")
        void createClassroom_ValidRequest_ReturnsCreated() throws Exception {
            CreateClassroomRequest request = new CreateClassroomRequest();
            request.setName("Math 101");
            request.setMaxStudents(30);

            ClassroomResponse response = new ClassroomResponse();
            response.setId(100L);
            response.setClassCode("MATH101");
            response.setClassName("Math 101");

            when(classroomService.createClassroom(any(CreateClassroomRequest.class), eq(1L))).thenReturn(response);

            mockMvc.perform(post("/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.classCode").value("MATH101"))
                    .andExpect(jsonPath("$.className").value("Math 101"));

            verify(classroomService, times(1)).createClassroom(any(CreateClassroomRequest.class), eq(1L));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when name is blank")
        void createClassroom_BlankName_Returns400BadRequest() throws Exception {
            CreateClassroomRequest request = new CreateClassroomRequest();
            request.setName("");
            request.setMaxStudents(30);

            mockMvc.perform(post("/classrooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(classroomService, never()).createClassroom(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("GET /classrooms Integration Tests")
    class GetClassroomsListEndpointTests {

        @Test
        @DisplayName("Should return list of classrooms for user and return 200 OK")
        void getClassroomsList_ValidUser_ReturnsOk() throws Exception {
            ClassroomResponse response = new ClassroomResponse();
            response.setId(100L);
            response.setClassCode("MATH101");

            when(classroomService.getClassroomsListById(1L)).thenReturn(Collections.singletonList(response));

            mockMvc.perform(get("/classrooms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(100L))
                    .andExpect(jsonPath("$[0].classCode").value("MATH101"));

            verify(classroomService, times(1)).getClassroomsListById(1L);
        }
    }

    @Nested
    @DisplayName("GET /classrooms/{classCode} Integration Tests")
    class GetClassroomByClassCodeEndpointTests {

        @Test
        @DisplayName("Should return classroom by classCode and return 200 OK")
        void getClassroomByClassCode_ValidClassCode_ReturnsOk() throws Exception {
            ClassroomResponse response = new ClassroomResponse();
            response.setId(100L);
            response.setClassCode("MATH101");

            when(classroomService.getClassroomByClassCode("MATH101", 1L)).thenReturn(response);

            mockMvc.perform(get("/classrooms/MATH101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.classCode").value("MATH101"));

            verify(classroomService, times(1)).getClassroomByClassCode("MATH101", 1L);
        }
    }

    @Nested
    @DisplayName("PUT /classrooms/{classCode} Integration Tests")
    class UpdateClassroomEndpointTests {

        @Test
        @DisplayName("Should update classroom successfully and return 200 OK")
        void updateClassroom_ValidRequest_ReturnsOk() throws Exception {
            UpdateClassroomRequest request = new UpdateClassroomRequest();
            request.setClassName("Updated Math");
            request.setMaxStudents(30);

            ClassroomResponse response = new ClassroomResponse();
            response.setId(100L);
            response.setClassCode("MATH101");
            response.setClassName("Updated Math");

            when(classroomService.updateClassroom(eq("MATH101"), any(UpdateClassroomRequest.class), eq(1L)))
                    .thenReturn(response);

            mockMvc.perform(put("/classrooms/MATH101")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.className").value("Updated Math"));

            verify(classroomService, times(1)).updateClassroom(eq("MATH101"), any(UpdateClassroomRequest.class), eq(1L));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when className is blank")
        void updateClassroom_BlankClassName_Returns400BadRequest() throws Exception {
            UpdateClassroomRequest request = new UpdateClassroomRequest();
            request.setClassName("");
            request.setMaxStudents(30);

            mockMvc.perform(put("/classrooms/MATH101")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(classroomService, never()).updateClassroom(anyString(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("DELETE /classrooms/{classCode} Integration Tests")
    class DeleteClassroomEndpointTests {

        @Test
        @DisplayName("Should delete classroom successfully and return 204 No Content")
        void deleteClassroom_ValidClassCode_ReturnsNoContent() throws Exception {
            doNothing().when(classroomService).deleteClassroom("MATH101", 1L);

            mockMvc.perform(delete("/classrooms/MATH101"))
                    .andExpect(status().isNoContent());

            verify(classroomService, times(1)).deleteClassroom("MATH101", 1L);
        }
    }
}
