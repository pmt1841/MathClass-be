package com.codegym.mathclass.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeRequest {

    @NotNull(message = "Điểm số không được để trống")
    @Min(value = 0, message = "Điểm số phải lớn hơn hoặc bằng 0")
    @Max(value = 10, message = "Điểm số phải nhỏ hơn hoặc bằng 10")
    private Double score;

    private String teacherFeedback;
}
