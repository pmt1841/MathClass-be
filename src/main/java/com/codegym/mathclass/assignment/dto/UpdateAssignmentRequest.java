package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssignmentRequest {

    @NotBlank(message = "Tiêu đề bài tập không được để trống")
    private String title;

    @NotBlank(message = "Mô tả bài tập không được để trống")
    private String description;

    /**
     * Chỉ áp dụng khi sửa bài tập PUBLISHED (có lớp cụ thể).
     * Với bài DRAFT/ARCHIVED thì bỏ qua trường này.
     */
    private LocalDateTime deadline;
}
