package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSubmissionHandwritingServiceImplTest {

        @Mock
        private AiPromptExecutionService aiPromptExecutionService;

        @Mock
        private PromptRenderService promptRenderService;

        @InjectMocks
        private AiSubmissionHandwritingServiceImpl aiSubmissionHandwritingService;

        @BeforeEach
        void setUp() {
                lenient().when(promptRenderService.renderPrompt(any())).thenAnswer(invocation -> {
                        RenderPromptRequest req = invocation.getArgument(0);
                        return RenderPromptResponse.builder()
                                        .promptCode(req != null ? req.getPromptCode() : "MOCK_PROMPT")
                                        .renderedPrompt("Rendered system prompt for "
                                                        + (req != null ? req.getPromptCode() : ""))
                                        .build();
                });
        }

        @Test
        @DisplayName("convertHandwritingToLatex: Trả về mã LaTeX sạch khi AI phản hồi")
        void convertHandwritingToLatex_ValidRequest_ReturnsCleanLatex() {
                HandwritingLatexRequest request = HandwritingLatexRequest.builder()
                                .imageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                                .mimeType("image/png")
                                .build();

                String rawAiOutput = "```latex\n\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n```";
                when(aiPromptExecutionService.executePromptWithImage(
                                eq(AiSubmissionHandwritingServiceImpl.TASK_CODE),
                                any(String.class),
                                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                                eq("image/png"),
                                eq(1L))).thenReturn(rawAiOutput);

                HandwritingLatexResponse response = aiSubmissionHandwritingService.convertHandwritingToLatex(request,
                                1L);

                assertThat(response).isNotNull();
                assertThat(response.getLatex()).isEqualTo("\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}");
                verify(promptRenderService).renderPrompt(
                                argThat(r -> AiSubmissionHandwritingServiceImpl.PROMPT_HANDWRITING_LATEX_CODE
                                                .equals(r.getPromptCode())));
        }

        @Test
        @DisplayName("convertHandwritingToLatex: Bóc tách dấu kẹp inline \\( và \\)")
        void convertHandwritingToLatex_InlineParenthesisDelimiter_StripsDelimiters() {
                HandwritingLatexRequest request = HandwritingLatexRequest.builder()
                                .imageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                                .mimeType("image/png")
                                .build();

                String rawAiOutput = "\\( x^2 + y^2 = 25 \\)";
                when(aiPromptExecutionService.executePromptWithImage(
                                eq(AiSubmissionHandwritingServiceImpl.TASK_CODE),
                                any(String.class),
                                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                                eq("image/png"),
                                eq(1L))).thenReturn(rawAiOutput);

                HandwritingLatexResponse response = aiSubmissionHandwritingService.convertHandwritingToLatex(request,
                                1L);

                assertThat(response).isNotNull();
                assertThat(response.getLatex()).isEqualTo("x^2 + y^2 = 25");
        }

        @Test
        @DisplayName("convertHandwritingToLatex: Ném ResourceNotFoundException khi System Prompt chưa được cấu hình")
        void convertHandwritingToLatex_PromptMissing_ThrowsResourceNotFoundException() {
                HandwritingLatexRequest request = HandwritingLatexRequest.builder()
                                .imageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                                .mimeType("image/png")
                                .build();

                when(promptRenderService.renderPrompt(any())).thenReturn(null);

                assertThatThrownBy(() -> aiSubmissionHandwritingService.convertHandwritingToLatex(request, 1L))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Chưa cấu hình System Prompt 'PROMPT_HANDWRITING_LATEX'");
        }

        @Test
        @DisplayName("normalizeSketchToGeometry: Trả về JSON hình học chuẩn")
        void normalizeSketchToGeometry_ValidRequest_ReturnsGeometryResponse() {
                SketchGeometryRequest request = SketchGeometryRequest.builder()
                                .canvasImageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                                .mimeType("image/png")
                                .build();

                String jsonOutput = "{\n"
                                + "  \"shapeType\": \"TRIANGLE_RIGHT\",\n"
                                + "  \"points\": [ {\"label\": \"A\", \"x\": 0, \"y\": 4} ]\n"
                                + "}";

                when(aiPromptExecutionService.executePromptWithImage(
                                eq(AiSubmissionHandwritingServiceImpl.TASK_CODE),
                                any(String.class),
                                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                                eq("image/png"),
                                eq(1L))).thenReturn(jsonOutput);

                SketchGeometryResponse response = aiSubmissionHandwritingService.normalizeSketchToGeometry(request, 1L);

                assertThat(response).isNotNull();
                assertThat(response.getShapeType()).isEqualTo("TRIANGLE_RIGHT");
                assertThat(response.getGeometryJson()).contains("TRIANGLE_RIGHT");
                verify(promptRenderService).renderPrompt(
                                argThat(r -> AiSubmissionHandwritingServiceImpl.PROMPT_SKETCH_GEOMETRY_CODE
                                                .equals(r.getPromptCode())));
        }

        @Test
        @DisplayName("normalizeSketchToGeometry: Trả về JSON đồ thị hàm số khi AI nhận diện được đồ thị")
        void normalizeSketchToGeometry_FunctionGraph_ReturnsFunctionGraphResponse() {
                SketchGeometryRequest request = SketchGeometryRequest.builder()
                                .canvasImageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                                .mimeType("image/png")
                                .build();

                String jsonOutput = "{\n"
                                + "  \"shapeType\": \"FUNCTION_GRAPH\",\n"
                                + "  \"boundingbox\": [-5, 5, 5, -5],\n"
                                + "  \"elements\": [\n"
                                + "    {\"type\": \"functiongraph\", \"id\": \"fg1\", \"parsedFunc\": \"-(x-2)**2 + 2\"}\n"
                                + "  ]\n"
                                + "}";

                when(aiPromptExecutionService.executePromptWithImage(
                                eq(AiSubmissionHandwritingServiceImpl.TASK_CODE),
                                any(String.class),
                                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                                eq("image/png"),
                                eq(1L))).thenReturn(jsonOutput);

                SketchGeometryResponse response = aiSubmissionHandwritingService.normalizeSketchToGeometry(request, 1L);

                assertThat(response).isNotNull();
                assertThat(response.getShapeType()).isEqualTo("FUNCTION_GRAPH");
                assertThat(response.getGeometryJson()).contains("functiongraph");
        }

        @Test
        @DisplayName("normalizeSketchToGeometry: Ném ResourceNotFoundException khi System Prompt chưa được cấu hình")
        void normalizeSketchToGeometry_PromptMissing_ThrowsResourceNotFoundException() {
                SketchGeometryRequest request = SketchGeometryRequest.builder()
                                .canvasImageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                                .mimeType("image/png")
                                .build();

                when(promptRenderService.renderPrompt(any())).thenReturn(null);

                assertThatThrownBy(() -> aiSubmissionHandwritingService.normalizeSketchToGeometry(request, 1L))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Chưa cấu hình System Prompt 'PROMPT_SKETCH_GEOMETRY'");
        }
}
