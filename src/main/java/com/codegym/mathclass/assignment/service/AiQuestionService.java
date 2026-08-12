package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;

public interface AiQuestionService {
    AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request, Long userId);

    default AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request) {
        return generateQuestion(request, null);
    }
}
