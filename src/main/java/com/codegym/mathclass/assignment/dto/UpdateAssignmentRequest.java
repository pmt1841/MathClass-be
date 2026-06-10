package com.codegym.mathclass.assignment.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssignmentRequest {

    private String title;

    private String description;

    private String content;

    /**
     * Chỉ áp dụng khi sửa bài tập PUBLISHED (có lớp cụ thể).
     * Với bài DRAFT/ARCHIVED thì bỏ qua trường này.
     */
    private LocalDateTime deadline;
}
