package com.codegym.mathclass.aiqueue.model.payload;

import com.codegym.mathclass.assignment.dto.request.GenerateQuestionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuestionJobPayload {

    private GenerateQuestionRequest request;
    private Long userId;
}
