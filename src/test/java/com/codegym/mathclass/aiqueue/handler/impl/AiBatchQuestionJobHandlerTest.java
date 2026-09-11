package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.model.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.model.payload.AiBatchQuestionJobPayload;
import com.codegym.mathclass.assignment.dto.request.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.response.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.service.AiBatchQuestionService;
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
class AiBatchQuestionJobHandlerTest {

    @Mock
    private AiBatchQuestionService aiBatchQuestionService;

    @Mock
    private AiCreditService aiCreditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiBatchQuestionJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiBatchQuestionJobHandler(aiBatchQuestionService, aiCreditService, objectMapper);
    }

    @Test
    @DisplayName("canHandle chỉ chấp nhận TASK_CODE là BATCH_QUESTION_GEN")
    void testCanHandle() {
        assertTrue(handler.canHandle("BATCH_QUESTION_GEN"));
        assertTrue(handler.canHandle("batch_question_gen"));
        assertFalse(handler.canHandle("QUESTION_GEN"));
    }

    @Test
    @DisplayName("execute deserialize payload và gọi batchGenerateQuestions thành công")
    void testExecuteSuccess() throws Exception {
        AiBatchQuestionJobPayload payload = new AiBatchQuestionJobPayload();
        payload.setTextContent("Đề thi toán 12");
        payload.setUserId(10L);
        payload.setGrade(12);

        String payloadJson = objectMapper.writeValueAsString(payload);

        AiJobMessage message = AiJobMessage.builder()
                .jobId("batch-job-1")
                .userId(10L)
                .taskCode(AiBatchQuestionJobHandler.TASK_CODE)
                .payloadJson(payloadJson)
                .reservedCredits(15)
                .build();

        BatchGenerateQuestionsResponse mockResponse = new BatchGenerateQuestionsResponse();
        mockResponse.setCompletionTokens(500);

        when(aiBatchQuestionService.batchGenerateQuestions(any(BatchGenerateQuestionsRequest.class), eq(10L), eq(false)))
                .thenReturn(mockResponse);
        when(aiCreditService.getCreditConfig(AiBatchQuestionJobHandler.TASK_CODE))
                .thenReturn(Optional.empty());

        AiJobExecutionResult result = handler.execute(message);

        assertNotNull(result);
        assertEquals(mockResponse, result.getResultData());
        assertTrue(result.getActualCredits() > 0);
        verify(aiBatchQuestionService).batchGenerateQuestions(any(BatchGenerateQuestionsRequest.class), eq(10L), eq(false));
    }
}
