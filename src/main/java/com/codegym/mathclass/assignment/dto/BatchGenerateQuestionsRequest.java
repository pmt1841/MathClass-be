package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerateQuestionsRequest {
    private MultipartFile file;
    private String textContent;
    private Integer grade;
    private String topic;
    private String questionType;
    private Boolean includeExplanation;
    private Boolean includeCanvasDiagram;
}
