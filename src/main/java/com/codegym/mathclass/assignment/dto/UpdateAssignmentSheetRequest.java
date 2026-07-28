package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateAssignmentSheetRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
    
    private String description;
    private com.codegym.mathclass.assignment.entity.AssignmentVisibility visibility;
    private java.util.List<ItemScoreDto> itemScores;

    @Data
    public static class ItemScoreDto {
        private long assignmentId;
        private Double maxScore;
    }
}
