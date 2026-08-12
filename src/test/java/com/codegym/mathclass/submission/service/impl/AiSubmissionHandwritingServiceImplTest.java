package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSubmissionHandwritingServiceImplTest {

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @InjectMocks
    private AiSubmissionHandwritingServiceImpl aiSubmissionHandwritingService;

    @Test
    @DisplayName("convertHandwritingToLatex: Trả về mã LaTeX sạch khi AI phản hồi")
    void convertHandwritingToLatex_ValidRequest_ReturnsCleanLatex() {
        HandwritingLatexRequest request = HandwritingLatexRequest.builder()
                .imageData("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                .mimeType("image/png")
                .build();

        String rawAiOutput = "```latex\n\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n```";
        when(aiPromptExecutionService.executePromptWithImage(
                eq(AiSubmissionHandwritingServiceImpl.HANDWRITING_TASK_CODE),
                any(String.class),
                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                eq("image/png"),
                eq(1L)
        )).thenReturn(rawAiOutput);

        HandwritingLatexResponse response = aiSubmissionHandwritingService.convertHandwritingToLatex(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getLatex()).isEqualTo("\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}");
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
                eq(AiSubmissionHandwritingServiceImpl.SKETCH_TASK_CODE),
                any(String.class),
                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                eq("image/png"),
                eq(1L)
        )).thenReturn(jsonOutput);

        SketchGeometryResponse response = aiSubmissionHandwritingService.normalizeSketchToGeometry(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getShapeType()).isEqualTo("TRIANGLE_RIGHT");
        assertThat(response.getGeometryJson()).contains("TRIANGLE_RIGHT");
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
                eq(AiSubmissionHandwritingServiceImpl.SKETCH_TASK_CODE),
                any(String.class),
                eq("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."),
                eq("image/png"),
                eq(1L)
        )).thenReturn(jsonOutput);

        SketchGeometryResponse response = aiSubmissionHandwritingService.normalizeSketchToGeometry(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getShapeType()).isEqualTo("FUNCTION_GRAPH");
        assertThat(response.getGeometryJson()).contains("functiongraph");
    }
}
