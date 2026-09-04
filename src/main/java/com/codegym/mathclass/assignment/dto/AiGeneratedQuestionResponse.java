package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedQuestionResponse {
    private String title;
    private String content;      // KaTeX math content
    private String explanation;  // Lời giải chi tiết
    private Integer grade;
    private String difficulty;
    private String topic;
    private String model;
    private CanvasDataResponse canvasData;
    private Integer completionTokens;
}
