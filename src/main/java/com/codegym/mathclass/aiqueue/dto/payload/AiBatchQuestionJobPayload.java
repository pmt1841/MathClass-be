package com.codegym.mathclass.aiqueue.dto.payload;

import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBatchQuestionJobPayload {

    private String textContent;
    private List<AssignmentImageResponse> extractedImages;
    private Integer grade;
    private String topic;
    private String questionType;
    private Boolean includeExplanation;
    private Boolean includeCanvasDiagram;
    private Long userId;
}
