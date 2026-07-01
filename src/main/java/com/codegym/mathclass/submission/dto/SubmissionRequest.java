package com.codegym.mathclass.submission.dto;

import com.codegym.mathclass.submission.entity.SubmissionStatus;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class SubmissionRequest {
    @NotNull(message = "ID bài tập không được để trống")
    private Long assignmentId;
    private String content;
    private SubmissionStatus status;
}
