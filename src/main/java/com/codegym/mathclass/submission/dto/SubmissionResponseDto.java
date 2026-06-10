package com.codegym.mathclass.submission.dto;

import com.codegym.mathclass.submission.entity.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubmissionResponseDto {
    private long id;
    private long assignmentId;
    private long studentId;
    private String studentName; 
    private String content;
    private SubmissionStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
}
