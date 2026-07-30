package com.codegym.mathclass.assignment.dto;

import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO cho PATCH /api/assignments/{id}/visibility
 * và PATCH /api/assignment-sheets/{id}/visibility
 * Chỉ cập nhật 1 field visibility — không đụng đến nội dung bài tập.
 */
@Data
public class UpdateVisibilityRequest {

    @NotNull(message = "Visibility không được để trống")
    private AssignmentVisibility visibility;
}
