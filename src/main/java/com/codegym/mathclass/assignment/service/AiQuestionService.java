package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.request.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.response.AiGeneratedQuestionResponse;

public interface AiQuestionService {
    AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request, Long userId);

    AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request, Long userId, boolean chargeCredits);

    default AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request) {
        return generateQuestion(request, null, true);
    }
}
