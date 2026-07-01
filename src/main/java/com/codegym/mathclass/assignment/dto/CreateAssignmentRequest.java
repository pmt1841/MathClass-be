package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssignmentRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;
    private String content;

    private List<AssignmentDrawingRequest> drawings;
    private List<AssignmentImageRequest> images;
}
