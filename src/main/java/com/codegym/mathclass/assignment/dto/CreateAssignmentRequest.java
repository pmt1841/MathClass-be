package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssignmentRequest {

    @NotBlank(message = "Tiêu đề bài tập không được để trống")
    private String title;

    @NotBlank(message = "Mô tả bài tập không được để trống")
    private String description;

    @NotBlank(message = "Nội dung bài tập không được để trống")
    private String content;
}
