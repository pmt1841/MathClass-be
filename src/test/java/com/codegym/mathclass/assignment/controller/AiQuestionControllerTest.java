package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.service.AiQuestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuestionControllerTest {

    @Mock
    private AiQuestionService aiQuestionService;

    @Mock
    private com.codegym.mathclass.assignment.service.AiBatchQuestionService aiBatchQuestionService;

    @InjectMocks
    private AiQuestionController aiQuestionController;

    @Test
    @DisplayName("Should generate question successfully and return 200 OK")
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

        var responseEntity = aiQuestionController.generateQuestion(req, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(mockResponse, responseEntity.getBody());

        verify(aiQuestionService).generateQuestion(req, null);
    }

    @Test
    @DisplayName("Should batch generate questions successfully and return 200 OK")
    void testBatchGenerateQuestions_Success() {
        com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest req =
                com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest.builder()
                        .textContent("Bài 1: Giải phương trình x + 1 = 2")
                        .build();

        com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse mockResponse =
                com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse.builder()
                        .suggestedTitle("Đề kiểm tra")
                        .totalQuestions(1)
                        .build();

        when(aiBatchQuestionService.batchGenerateQuestions(any(), any())).thenReturn(mockResponse);

        var responseEntity = aiQuestionController.batchGenerateQuestions(req, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(mockResponse, responseEntity.getBody());

        verify(aiBatchQuestionService).batchGenerateQuestions(req, null);
    }

    @Test
    @DisplayName("Should forward exception when service throws AiGenerationException")
    void testGenerateQuestion_ServiceThrowsException() {
        GenerateQuestionRequest req = GenerateQuestionRequest.builder()
                .prompt("Cho tam giác ABC...")
                .grade(9)
                .build();

        when(aiQuestionService.generateQuestion(any(), any()))
                .thenThrow(new com.codegym.mathclass.assignment.exception.AiGenerationException(503, "Service unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.codegym.mathclass.assignment.exception.AiGenerationException.class,
                () -> aiQuestionController.generateQuestion(req, null)
        );
    }
}

