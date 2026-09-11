package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.model.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.model.payload.AiHandwritingJobPayload;
import com.codegym.mathclass.submission.dto.request.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.request.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.response.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.response.SketchGeometryResponse;
import com.codegym.mathclass.submission.service.AiSubmissionHandwritingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiHandwritingJobHandlerTest {

    @Mock
    private AiSubmissionHandwritingService handwritingService;

    @Mock
    private AiCreditService aiCreditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiHandwritingJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiHandwritingJobHandler(handwritingService, aiCreditService, objectMapper);
    }

    @Test
    @DisplayName("canHandle chỉ chấp nhận CANVAS_LATEX")
    void testCanHandle() {
        assertTrue(handler.canHandle("CANVAS_LATEX"));
        assertTrue(handler.canHandle("canvas_latex"));
        assertFalse(handler.canHandle("QUESTION_GEN"));
    }

    @Test
    @DisplayName("execute xử lý OCR chữ viết tay sang LaTeX")
    void testExecuteLatexConversion() throws Exception {
        AiHandwritingJobPayload payload = new AiHandwritingJobPayload();
        payload.setSubTask("HANDWRITING_LATEX");
        payload.setUserId(20L);
        payload.setLatexRequest(new HandwritingLatexRequest());

        String payloadJson = objectMapper.writeValueAsString(payload);

        AiJobMessage message = AiJobMessage.builder()
                .jobId("canvas-job-1")
                .userId(20L)
                .taskCode(AiHandwritingJobHandler.TASK_CODE)
                .payloadJson(payloadJson)
                .reservedCredits(2)
                .build();

        HandwritingLatexResponse mockResponse = new HandwritingLatexResponse();
        mockResponse.setCompletionTokens(100);

        when(handwritingService.convertHandwritingToLatex(any(HandwritingLatexRequest.class), eq(20L), eq(false)))
                .thenReturn(mockResponse);
        when(aiCreditService.getCreditConfig(AiHandwritingJobHandler.TASK_CODE))
                .thenReturn(Optional.empty());

        AiJobExecutionResult result = handler.execute(message);

        assertNotNull(result);
        assertEquals(mockResponse, result.getResultData());
        verify(handwritingService).convertHandwritingToLatex(any(HandwritingLatexRequest.class), eq(20L), eq(false));
    }

    @Test
    @DisplayName("execute xử lý nhận diện hình học SKETCH_GEOMETRY")
    void testExecuteSketchGeometry() throws Exception {
        AiHandwritingJobPayload payload = new AiHandwritingJobPayload();
        payload.setSubTask("SKETCH_GEOMETRY");
        payload.setUserId(21L);
        payload.setSketchRequest(new SketchGeometryRequest());

        String payloadJson = objectMapper.writeValueAsString(payload);

        AiJobMessage message = AiJobMessage.builder()
                .jobId("canvas-job-2")
                .userId(21L)
                .taskCode(AiHandwritingJobHandler.TASK_CODE)
                .payloadJson(payloadJson)
                .reservedCredits(2)
                .build();

        SketchGeometryResponse mockResponse = new SketchGeometryResponse();
        mockResponse.setCompletionTokens(120);

        when(handwritingService.normalizeSketchToGeometry(any(SketchGeometryRequest.class), eq(21L), eq(false)))
                .thenReturn(mockResponse);
        when(aiCreditService.getCreditConfig(AiHandwritingJobHandler.TASK_CODE))
                .thenReturn(Optional.empty());

        AiJobExecutionResult result = handler.execute(message);

        assertNotNull(result);
        assertEquals(mockResponse, result.getResultData());
        verify(handwritingService).normalizeSketchToGeometry(any(SketchGeometryRequest.class), eq(21L), eq(false));
    }
}
