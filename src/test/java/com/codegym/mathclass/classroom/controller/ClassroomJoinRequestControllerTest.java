package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.JoinRequestRequest;
import com.codegym.mathclass.classroom.dto.JoinRequestResponse;
import com.codegym.mathclass.classroom.dto.ProcessJoinRequestDto;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.service.ClassroomJoinRequestService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClassroomJoinRequestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassroomJoinRequestService joinRequestService;

    @InjectMocks
    private ClassroomJoinRequestController joinRequestController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                2L, "Student", "student@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(joinRequestController)
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
    @DisplayName("POST /classrooms/join-requests Integration Tests")
    class RequestToJoinEndpointTests {

        @Test
        @DisplayName("Should create join request successfully and return 201 Created")
        void requestToJoin_ValidRequest_ReturnsCreated() throws Exception {
            JoinRequestRequest request = new JoinRequestRequest();
            request.setClassCode("ABC12345");

            JoinRequestResponse response = JoinRequestResponse.builder()
                    .id(100L)
                    .status(JoinRequestStatus.PENDING)
                    .classCode("ABC12345")
                    .build();

            when(joinRequestService.createJoinRequest(any(JoinRequestRequest.class), eq(2L))).thenReturn(response);

            mockMvc.perform(post("/classrooms/join-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.classCode").value("ABC12345"));

            verify(joinRequestService, times(1)).createJoinRequest(any(JoinRequestRequest.class), eq(2L));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when classCode is blank")
        void requestToJoin_BlankClassCode_Returns400BadRequest() throws Exception {
            JoinRequestRequest request = new JoinRequestRequest();
            request.setClassCode("");

            mockMvc.perform(post("/classrooms/join-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(joinRequestService, never()).createJoinRequest(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("GET /classrooms/join-requests/me Integration Tests")
    class GetMyJoinRequestsEndpointTests {

        @Test
        @DisplayName("Should return my join requests list and 200 OK")
        void getMyJoinRequests_ValidUser_ReturnsOk() throws Exception {
            JoinRequestResponse response = JoinRequestResponse.builder()
                    .id(100L)
                    .status(JoinRequestStatus.PENDING)
                    .build();

            when(joinRequestService.getMyJoinRequests(2L)).thenReturn(List.of(response));

            mockMvc.perform(get("/classrooms/join-requests/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(100L))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));

            verify(joinRequestService, times(1)).getMyJoinRequests(2L);
        }
    }

    @Nested
    @DisplayName("GET /classrooms/{classCode}/join-requests Integration Tests")
    class GetPendingRequestsEndpointTests {

        @Test
        @DisplayName("Should return pending join requests for teacher and 200 OK")
        void getPendingRequests_Teacher_ReturnsOk() throws Exception {
            JoinRequestResponse response = JoinRequestResponse.builder()
                    .id(100L)
                    .status(JoinRequestStatus.PENDING)
                    .build();

            when(joinRequestService.getPendingJoinRequests("ABC12345", 2L)).thenReturn(List.of(response));

            mockMvc.perform(get("/classrooms/ABC12345/join-requests"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(100L))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));

            verify(joinRequestService, times(1)).getPendingJoinRequests("ABC12345", 2L);
        }
    }

    @Nested
    @DisplayName("PUT /classrooms/join-requests/{requestId} Integration Tests")
    class ProcessRequestEndpointTests {

        @Test
        @DisplayName("Should process join request successfully and return 200 OK")
        void processRequest_ValidRequest_ReturnsOk() throws Exception {
            ProcessJoinRequestDto requestDto = new ProcessJoinRequestDto();
            requestDto.setStatus(JoinRequestStatus.APPROVED);

            JoinRequestResponse response = JoinRequestResponse.builder()
                    .id(100L)
                    .status(JoinRequestStatus.APPROVED)
                    .build();

            when(joinRequestService.processJoinRequest(eq(100L), any(ProcessJoinRequestDto.class), eq(2L)))
                    .thenReturn(response);

            mockMvc.perform(put("/classrooms/join-requests/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            verify(joinRequestService, times(1)).processJoinRequest(eq(100L), any(ProcessJoinRequestDto.class), eq(2L));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when status is null")
        void processRequest_NullStatus_Returns400BadRequest() throws Exception {
            ProcessJoinRequestDto requestDto = new ProcessJoinRequestDto();
            requestDto.setStatus(null);

            mockMvc.perform(put("/classrooms/join-requests/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isBadRequest());

            verify(joinRequestService, never()).processJoinRequest(anyLong(), any(), anyLong());
        }
    }
}
