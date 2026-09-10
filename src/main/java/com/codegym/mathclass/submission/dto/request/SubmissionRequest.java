package com.codegym.mathclass.submission.dto.request;

import com.codegym.mathclass.submission.entity.SubmissionStatus;
import lombok.Data;

@Data
public class SubmissionRequest {
    private Long assignmentId;
    private String content;
    private SubmissionStatus status;
}
