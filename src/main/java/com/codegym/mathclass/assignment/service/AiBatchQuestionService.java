package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.aiqueue.dto.payload.AiBatchQuestionJobPayload;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse;

public interface AiBatchQuestionService {
    BatchGenerateQuestionsResponse batchGenerateQuestions(BatchGenerateQuestionsRequest request, Long userId);

    BatchGenerateQuestionsResponse batchGenerateQuestions(BatchGenerateQuestionsRequest request, Long userId, boolean chargeCredits);

    AiBatchQuestionJobPayload prepareBatchJobPayload(BatchGenerateQuestionsRequest request, Long userId);
}

