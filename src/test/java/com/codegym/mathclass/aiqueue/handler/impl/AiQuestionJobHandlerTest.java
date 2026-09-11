package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.model.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.model.payload.AiQuestionJobPayload;
import com.codegym.mathclass.assignment.dto.request.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.response.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.service.AiQuestionService;
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
class AiQuestionJobHandlerTest {

    @Mock
    private AiQuestionService aiQuestionService;

    @Mock
    private AiCreditService aiCreditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiQuestionJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiQuestionJobHandler(aiQuestionService, aiCreditService, objectMapper);
    }

    @Test
    @DisplayName("canHandle chỉ chấp nhận QUESTION_GEN")
    void testCanHandle() {
        assertTrue(handler.canHandle("QUESTION_GEN"));
        assertTrue(handler.canHandle("question_gen"));
        assertFalse(handler.canHandle("CANVAS_LATEX"));
    }

    @Test
    @DisplayName("execute gọi generateQuestion thành công")
    void testExecuteSuccess() throws Exception {
        AiQuestionJobPayload payload = new AiQuestionJobPayload();
        payload.setUserId(30L);
        payload.setRequest(new GenerateQuestionRequest());

        String payloadJson = objectMapper.writeValueAsString(payload);

        AiJobMessage message = AiJobMessage.builder()
                .jobId("q-job-1")
                .userId(30L)
                .taskCode(AiQuestionJobHandler.TASK_CODE)
                .payloadJson(payloadJson)
                .reservedCredits(3)
                .build();

        AiGeneratedQuestionResponse mockResponse = new AiGeneratedQuestionResponse();
        mockResponse.setCompletionTokens(150);

        when(aiQuestionService.generateQuestion(any(GenerateQuestionRequest.class), eq(30L), eq(false)))
                .thenReturn(mockResponse);
        when(aiCreditService.getCreditConfig(AiQuestionJobHandler.TASK_CODE))
                .thenReturn(Optional.empty());

        AiJobExecutionResult result = handler.execute(message);

        assertNotNull(result);
        assertEquals(mockResponse, result.getResultData());
        verify(aiQuestionService).generateQuestion(any(GenerateQuestionRequest.class), eq(30L), eq(false));
    }
}
