package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;

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

    private AssignmentVisibility visibility;

    private List<AssignmentDrawingRequest> drawings;
    private List<AssignmentImageRequest> images;
}
