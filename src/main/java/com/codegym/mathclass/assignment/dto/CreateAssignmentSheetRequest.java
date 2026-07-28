package com.codegym.mathclass.assignment.dto;

import lombok.Data;
import java.util.List;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;

@Data
public class CreateAssignmentSheetRequest {
    private String title;
    private String description;
    private AssignmentVisibility visibility;
    private List<Long> assignmentIds;
}
