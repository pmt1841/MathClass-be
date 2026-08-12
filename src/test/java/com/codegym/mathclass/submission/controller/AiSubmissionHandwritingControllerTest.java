package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import com.codegym.mathclass.submission.service.AiSubmissionHandwritingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiSubmissionHandwritingControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CustomUserDetails mockUserDetails;

    @Mock
    private AiSubmissionHandwritingService aiSubmissionHandwritingService;

    @InjectMocks
    private AiSubmissionHandwritingController aiSubmissionHandwritingController;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                1L, "Student", "student@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(aiSubmissionHandwritingController)
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

    @Test
    @DisplayName("POST /submissions/ai/handwriting-to-latex: Thành công trả về 200 OK")
    void convertHandwritingToLatex_ValidRequest_ReturnsOk() throws Exception {
        HandwritingLatexRequest request = HandwritingLatexRequest.builder()
                .imageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                .mimeType("image/png")
                .build();

        HandwritingLatexResponse response = HandwritingLatexResponse.builder()
                .latex("\\frac{a}{b}")
                .rawAiOutput("\\frac{a}{b}")
                .build();

        when(aiSubmissionHandwritingService.convertHandwritingToLatex(any(HandwritingLatexRequest.class), eq(1L)))
                .thenReturn(response);

        mockMvc.perform(post("/submissions/ai/handwriting-to-latex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latex").value("\\frac{a}{b}"));
    }

    @Test
    @DisplayName("POST /submissions/ai/sketch-to-geometry: Thành công trả về 200 OK")
    void normalizeSketchToGeometry_ValidRequest_ReturnsOk() throws Exception {
        SketchGeometryRequest request = SketchGeometryRequest.builder()
                .canvasImageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                .mimeType("image/png")
                .build();

        SketchGeometryResponse response = SketchGeometryResponse.builder()
                .shapeType("TRIANGLE_RIGHT")
                .geometryJson("{\"shapeType\":\"TRIANGLE_RIGHT\"}")
                .build();

        when(aiSubmissionHandwritingService.normalizeSketchToGeometry(any(SketchGeometryRequest.class), eq(1L)))
                .thenReturn(response);

        mockMvc.perform(post("/submissions/ai/sketch-to-geometry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shapeType").value("TRIANGLE_RIGHT"));
    }
}
