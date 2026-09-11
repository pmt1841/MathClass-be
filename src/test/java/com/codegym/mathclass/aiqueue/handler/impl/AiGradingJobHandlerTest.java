package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.model.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.model.payload.AiGradingJobPayload;
import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.codegym.mathclass.submission.service.AiGradingService;
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
class AiGradingJobHandlerTest {

    @Mock
    private AiGradingService aiGradingService;

    @Mock
    private AiCreditService aiCreditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiGradingJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiGradingJobHandler(aiGradingService, aiCreditService, objectMapper);
    }

    @Test
    @DisplayName("canHandle chỉ chấp nhận SUBMISSION_GRADING")
    void testCanHandle() {
        assertTrue(handler.canHandle("SUBMISSION_GRADING"));
        assertTrue(handler.canHandle("submission_grading"));
        assertFalse(handler.canHandle("BATCH_QUESTION_GEN"));
    }

    @Test
    @DisplayName("execute deserialize payload và gọi requestAiGrading thành công")
    void testExecuteSuccess() throws Exception {
        AiGradingJobPayload payload = new AiGradingJobPayload();
        payload.setSubmissionId(101L);
        payload.setTeacherId(5L);
        payload.setRequest(new AiGradingRequest());

        String payloadJson = objectMapper.writeValueAsString(payload);

        AiJobMessage message = AiJobMessage.builder()
                .jobId("grading-job-1")
                .userId(5L)
                .taskCode(AiGradingJobHandler.TASK_CODE)
                .payloadJson(payloadJson)
                .reservedCredits(5)
                .build();

        AiGradingResponse mockResponse = new AiGradingResponse();
        mockResponse.setCompletionTokens(200);

        when(aiGradingService.requestAiGrading(eq(101L), any(AiGradingRequest.class), eq(5L), eq(false)))
                .thenReturn(mockResponse);
        when(aiCreditService.getCreditConfig(AiGradingJobHandler.TASK_CODE))
                .thenReturn(Optional.empty());

        AiJobExecutionResult result = handler.execute(message);

        assertNotNull(result);
        assertEquals(mockResponse, result.getResultData());
        verify(aiGradingService).requestAiGrading(eq(101L), any(AiGradingRequest.class), eq(5L), eq(false));
    }
}
