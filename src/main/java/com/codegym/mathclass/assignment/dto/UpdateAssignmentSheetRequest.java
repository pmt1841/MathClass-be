package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;

@Data
public class UpdateAssignmentSheetRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
    
    private String description;
    private AssignmentVisibility visibility;
    private List<ItemScoreDto> itemScores;

    @Data
    public static class ItemScoreDto {
        private long assignmentId;
        private Double maxScore;
    }
}
