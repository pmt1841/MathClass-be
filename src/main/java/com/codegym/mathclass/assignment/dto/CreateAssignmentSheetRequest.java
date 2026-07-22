package com.codegym.mathclass.assignment.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateAssignmentSheetRequest {
    private String title;
    private String description;
    private List<Long> assignmentIds;
}
