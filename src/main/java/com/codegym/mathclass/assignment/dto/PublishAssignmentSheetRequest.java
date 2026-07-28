package com.codegym.mathclass.assignment.dto;

import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;

@Data
public class PublishAssignmentSheetRequest {
    private Long masterSheetId;
    private String title;
    private String description;
    private com.codegym.mathclass.assignment.entity.AssignmentVisibility visibility;
    private List<Long> assignmentIds;
    private List<TargetClass> targets;
    private List<ItemScoreDto> itemScores;

    @Data
    public static class TargetClass {
        private String classCode;
        private LocalDateTime deadline;
    }

    @Data
    public static class ItemScoreDto {
        private long assignmentId;
        private Double maxScore;
    }
}
