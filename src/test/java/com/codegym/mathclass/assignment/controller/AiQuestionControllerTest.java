package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.AiJobSubmitResponse;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.assignment.service.AiBatchQuestionService;
import com.codegym.mathclass.assignment.service.AiQuestionService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuestionControllerTest {

    @Mock
    private AiQuestionService aiQuestionService;

    @Mock
    private AiBatchQuestionService aiBatchQuestionService;

    @Mock
    private AiJobService aiJobService;

    @InjectMocks
    private AiQuestionController aiQuestionController;

    @Test
    @DisplayName("Should generate question successfully (sync) and return 200 OK")
    void testGenerateQuestion_Success() {
        GenerateQuestionRequest req = GenerateQuestionRequest.builder()
                .prompt("Cho tam giác ABC...")
                .grade(9)
                .difficulty("THONG_HIEU")
                .build();

        AiGeneratedQuestionResponse mockResponse = AiGeneratedQuestionResponse.builder()
                .title("Bài toán tam giác")
                .content("Cho tam giác $ABC$...")
                .grade(9)
                .difficulty("THONG_HIEU")
                .build();

        when(aiQuestionService.generateQuestion(any(), any())).thenReturn(mockResponse);

        ResponseEntity<?> responseEntity = aiQuestionController.generateQuestion(req, false, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());

        verify(aiQuestionService).generateQuestion(req, null);
    }

    @Test
    @DisplayName("Should enqueue question generation job (async=true) and return 202 Accepted")
    void testGenerateQuestion_Async() {
        GenerateQuestionRequest req = GenerateQuestionRequest.builder()
                .prompt("Cho tam giác ABC...")
                .grade(9)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(
                5L, "Teacher", "teacher@mathclass.com", "pass", true, null, List.of()
        );

        AiJobSubmitResponse mockSubmit = AiJobSubmitResponse.builder()
                .jobId("job-gen-123")
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.QUEUED)
                .createdAt(Instant.now())
                .message("Đã tiếp nhận")
                .build();

        when(aiJobService.submitJob(eq("QUESTION_GEN"), eq(5L), any())).thenReturn(mockSubmit);

        ResponseEntity<?> responseEntity = aiQuestionController.generateQuestion(req, true, userDetails);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.ACCEPTED, responseEntity.getStatusCode());
        assertEquals(mockSubmit, responseEntity.getBody());

        verify(aiJobService).submitJob(eq("QUESTION_GEN"), eq(5L), any());
    }

    @Test
    @DisplayName("Should batch generate questions successfully (sync) and return 200 OK")
    void testBatchGenerateQuestions_Success() {
        BatchGenerateQuestionsRequest req = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình x + 1 = 2")
                .build();

        BatchGenerateQuestionsResponse mockResponse = BatchGenerateQuestionsResponse.builder()
                .suggestedTitle("Đề kiểm tra")
                .totalQuestions(1)
                .build();

        when(aiBatchQuestionService.batchGenerateQuestions(any(), any())).thenReturn(mockResponse);

        ResponseEntity<?> responseEntity = aiQuestionController.batchGenerateQuestions(req, false, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());

        verify(aiBatchQuestionService).batchGenerateQuestions(req, null);
    }

    @Test
    @DisplayName("Should enqueue batch question generation job (async=true) and return 202 Accepted")
    void testBatchGenerateQuestions_Async() {
        BatchGenerateQuestionsRequest req = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình x + 1 = 2")
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(
                5L, "Teacher", "teacher@mathclass.com", "pass", true, null, List.of()
        );

        com.codegym.mathclass.aiqueue.dto.payload.AiBatchQuestionJobPayload mockPayload =
                com.codegym.mathclass.aiqueue.dto.payload.AiBatchQuestionJobPayload.builder()
                        .textContent("Bài 1: Giải phương trình x + 1 = 2")
                        .userId(5L)
                        .build();

        AiJobSubmitResponse mockSubmit = AiJobSubmitResponse.builder()
                .jobId("job-batch-123")
                .taskCode("BATCH_QUESTION_GEN")
                .status(AiJobStatus.QUEUED)
                .createdAt(Instant.now())
                .message("Đã tiếp nhận")
                .build();

        when(aiBatchQuestionService.prepareBatchJobPayload(eq(req), eq(5L))).thenReturn(mockPayload);
        when(aiJobService.submitJob(eq("BATCH_QUESTION_GEN"), eq(5L), eq(mockPayload))).thenReturn(mockSubmit);

        ResponseEntity<?> responseEntity = aiQuestionController.batchGenerateQuestions(req, true, userDetails);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.ACCEPTED, responseEntity.getStatusCode());
        assertEquals(mockSubmit, responseEntity.getBody());

        verify(aiBatchQuestionService).prepareBatchJobPayload(req, 5L);
        verify(aiJobService).submitJob(eq("BATCH_QUESTION_GEN"), eq(5L), eq(mockPayload));
    }

    @Test
    @DisplayName("Should forward exception when service throws AiGenerationException")
    void testGenerateQuestion_ServiceThrowsException() {
        GenerateQuestionRequest req = GenerateQuestionRequest.builder()
                .prompt("Cho tam giác ABC...")
                .grade(9)
                .build();

        when(aiQuestionService.generateQuestion(any(), any()))
                .thenThrow(new AiGenerationException(503, "Service unavailable"));

        assertThrows(
                AiGenerationException.class,
                () -> aiQuestionController.generateQuestion(req, false, null)
        );
    }
}
