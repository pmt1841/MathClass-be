package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateAssignmentSheetRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
    
    private String description;
}
