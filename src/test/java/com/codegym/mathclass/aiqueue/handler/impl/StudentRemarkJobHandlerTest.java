package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.model.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.model.payload.StudentRemarkJobPayload;
import com.codegym.mathclass.classroom.dto.request.AiStudentRemarkEvaluateRequest;
import com.codegym.mathclass.classroom.dto.response.AiStudentRemarkEvaluationResponse;
import com.codegym.mathclass.classroom.service.StudentRemarkAiService;
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
class StudentRemarkJobHandlerTest {

    @Mock
    private StudentRemarkAiService studentRemarkAiService;

    @Mock
    private AiCreditService aiCreditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StudentRemarkJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StudentRemarkJobHandler(studentRemarkAiService, aiCreditService, objectMapper);
    }

    @Test
    @DisplayName("canHandle chỉ chấp nhận STUDENT_REMARK")
    void testCanHandle() {
        assertTrue(handler.canHandle("STUDENT_REMARK"));
        assertTrue(handler.canHandle("student_remark"));
        assertFalse(handler.canHandle("QUESTION_GEN"));
    }

    @Test
    @DisplayName("execute gọi evaluateStudentProgress thành công")
    void testExecuteSuccess() throws Exception {
        StudentRemarkJobPayload payload = new StudentRemarkJobPayload();
        payload.setClassCode("CLASS10A");
        payload.setStudentId(101L);
        payload.setCurrentUserId(50L);
        payload.setRequest(new AiStudentRemarkEvaluateRequest());

        String payloadJson = objectMapper.writeValueAsString(payload);

        AiJobMessage message = AiJobMessage.builder()
                .jobId("remark-job-1")
                .userId(50L)
                .taskCode(StudentRemarkJobHandler.TASK_CODE)
                .payloadJson(payloadJson)
                .reservedCredits(4)
                .build();

        AiStudentRemarkEvaluationResponse mockResponse = new AiStudentRemarkEvaluationResponse();
        mockResponse.setCompletionTokens(180);

        when(studentRemarkAiService.evaluateStudentProgress(
                eq("CLASS10A"),
                eq(101L),
                eq(50L),
                any(AiStudentRemarkEvaluateRequest.class),
                eq(false)
        )).thenReturn(mockResponse);

        when(aiCreditService.getCreditConfig(StudentRemarkJobHandler.TASK_CODE))
                .thenReturn(Optional.empty());

        AiJobExecutionResult result = handler.execute(message);

        assertNotNull(result);
        assertEquals(mockResponse, result.getResultData());
        verify(studentRemarkAiService).evaluateStudentProgress(
                eq("CLASS10A"),
                eq(101L),
                eq(50L),
                any(AiStudentRemarkEvaluateRequest.class),
                eq(false)
        );
    }
}
