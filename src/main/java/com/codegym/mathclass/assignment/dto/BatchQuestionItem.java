package com.codegym.mathclass.assignment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchQuestionItem {
    private String id;
    private String title;
    private String description;
    private String content;
    private String explanation;
    private String difficulty;
    private BigDecimal suggestedScore;
}
