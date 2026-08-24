package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssignmentSheetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssignmentSheetService assignmentSheetService;

    @InjectMocks
    private AssignmentSheetController assignmentSheetController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(assignmentSheetController)
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
    @DisplayName("POST /assignment-sheets Integration Tests")
    class PublishAssignmentSheetEndpointTests {

        @Test
        @DisplayName("Should publish assignment sheet successfully")
        void publishAssignmentSheet_ValidRequest_ReturnsCreated() throws Exception {
            PublishAssignmentSheetRequest request = new PublishAssignmentSheetRequest();
            request.setTitle("Đề thi toán");
            request.setAssignmentIds(List.of(10L, 20L));

            doNothing().when(assignmentSheetService).publishAssignmentSheet(any(PublishAssignmentSheetRequest.class), eq(1L));

            mockMvc.perform(post("/assignment-sheets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(assignmentSheetService, times(1)).publishAssignmentSheet(any(PublishAssignmentSheetRequest.class), eq(1L));
        }
    }

    @Nested
    @DisplayName("PUT /assignment-sheets/{id} Integration Tests")
    class UpdateAssignmentSheetEndpointTests {

        @Test
        @DisplayName("Should update assignment sheet successfully")
        void updateAssignmentSheet_ValidRequest_ReturnsOk() throws Exception {
            UpdateAssignmentSheetRequest request = new UpdateAssignmentSheetRequest();
            request.setTitle("Đề thi đã sửa");
            request.setDescription("Mô tả mới");

            AssignmentSheetResponse response = new AssignmentSheetResponse();
            response.setId(5L);
            response.setTitle("Đề thi đã sửa");

            when(assignmentSheetService.updateAssignmentSheet(eq(5L), any(UpdateAssignmentSheetRequest.class), eq(1L)))
                    .thenReturn(response);

            mockMvc.perform(put("/assignment-sheets/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5L))
                    .andExpect(jsonPath("$.title").value("Đề thi đã sửa"));

            verify(assignmentSheetService, times(1)).updateAssignmentSheet(eq(5L), any(UpdateAssignmentSheetRequest.class), eq(1L));
        }
    }

    @Nested
    @DisplayName("GET /assignment-sheets Integration Tests")
    class GetAssignmentSheetsEndpointTests {

        @Test
        @DisplayName("Should return paginated list of assignment sheets")
        void getAssignmentSheetsForCurrentUser_ValidRequest_ReturnsOkAndPage() throws Exception {
            AssignmentSheetResponse response = new AssignmentSheetResponse();
            response.setId(5L);
            response.setTitle("Đề thi giữa kỳ");

            Page<AssignmentSheetResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);

            when(assignmentSheetService.getAssignmentSheetsForCurrentUser(eq(1L), eq("TEACHER"), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/assignment-sheets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(5L))
                    .andExpect(jsonPath("$.content[0].title").value("Đề thi giữa kỳ"));

            verify(assignmentSheetService, times(1)).getAssignmentSheetsForCurrentUser(eq(1L), eq("TEACHER"), any(), any(), any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("DELETE /assignment-sheets/{id} Integration Tests")
    class DeleteAssignmentSheetEndpointTests {

        @Test
        @DisplayName("Should delete assignment sheet successfully and return 204 No Content")
        void deleteAssignmentSheet_ValidId_ReturnsNoContent() throws Exception {
            doNothing().when(assignmentSheetService).deleteAssignmentSheet(eq(5L), eq(1L));

            mockMvc.perform(delete("/assignment-sheets/5"))
                    .andExpect(status().isNoContent());

            verify(assignmentSheetService, times(1)).deleteAssignmentSheet(eq(5L), eq(1L));
        }
    }
}
