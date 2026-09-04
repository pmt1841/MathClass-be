package com.codegym.mathclass.aiqueue.dto.payload;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
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
