package com.codegym.mathclass.assignment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchGenerateQuestionsResponse {
    private String suggestedTitle;
    private String suggestedDescription;
    @Builder.Default
    private List<BatchQuestionItem> questions = new ArrayList<>();
    private Integer totalQuestions;
    @Builder.Default
    private List<AssignmentImageDto> extractedImages = new ArrayList<>();
    private String model;
    private Integer completionTokens;
}
