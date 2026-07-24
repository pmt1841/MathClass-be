package com.codegym.mathclass.assignment.dto;

import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;

@Data
public class PublishAssignmentSheetRequest {
    private String title;
    private String description;
    private com.codegym.mathclass.assignment.entity.AssignmentVisibility visibility;
    private List<Long> assignmentIds;
    private List<TargetClass> targets;

    @Data
    public static class TargetClass {
        private String classCode;
        private LocalDateTime deadline;
    }
}
