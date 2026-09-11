package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.request.StudentHintRequest;
import com.codegym.mathclass.submission.dto.response.HintHistoryResponse;
import com.codegym.mathclass.submission.dto.response.StudentHintResponse;
import com.codegym.mathclass.submission.service.AiHintService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StudentHintControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiHintService aiHintService;

    @InjectMocks
    private StudentHintController studentHintController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                10L, "Student A", "student@mathclass.edu.vn", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(studentHintController)
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
    @DisplayName("POST /submissions/assignments/{assignmentId}/hints Tests")
    class RequestHintTests {

        @Test
        @DisplayName("Should request hint successfully and return 200 OK")
        void requestHint_Success() throws Exception {
            StudentHintRequest request = StudentHintRequest.builder()
                    .currentContent("Tôi đã rút gọn được phương trình đến 2x = 4...")
                    .build();

            StudentHintResponse response = StudentHintResponse.builder()
                    .id(1L)
                    .submissionId(100L)
                    .hintNumber(1)
                    .maxHints(3)
                    .remainingHints(2)
                    .hintContent("Hãy chia cả hai vế cho hệ số của x để tìm nghiệm.")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(aiHintService.requestHint(eq(50L), any(StudentHintRequest.class), eq("student@mathclass.edu.vn")))
                    .thenReturn(response);

            mockMvc.perform(post("/submissions/assignments/50/hints")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.hintNumber").value(1))
                    .andExpect(jsonPath("$.maxHints").value(3))
                    .andExpect(jsonPath("$.remainingHints").value(2))
                    .andExpect(jsonPath("$.hintContent").value("Hãy chia cả hai vế cho hệ số của x để tìm nghiệm."));
        }
    }

    @Nested
    @DisplayName("GET /submissions/{submissionId}/hints Tests")
    class GetHintHistoryTests {

        @Test
        @DisplayName("Should get hint history successfully and return 200 OK")
        void getHintHistory_Success() throws Exception {
            HintHistoryResponse response = HintHistoryResponse.builder()
                    .submissionId(100L)
                    .totalUsed(1)
                    .maxHints(3)
                    .remainingHints(2)
                    .hints(List.of())
                    .build();

            when(aiHintService.getHintHistory(eq(100L), eq("student@mathclass.edu.vn")))
                    .thenReturn(response);

            mockMvc.perform(get("/submissions/100/hints")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionId").value(100L))
                    .andExpect(jsonPath("$.totalUsed").value(1))
                    .andExpect(jsonPath("$.remainingHints").value(2));
        }
    }
}
