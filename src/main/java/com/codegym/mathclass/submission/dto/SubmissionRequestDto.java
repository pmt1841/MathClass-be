package com.codegym.mathclass.submission.dto;

import com.codegym.mathclass.submission.entity.SubmissionStatus;
import lombok.Data;

@Data
public class SubmissionRequestDto {
    private String content;
    private SubmissionStatus status;
}
