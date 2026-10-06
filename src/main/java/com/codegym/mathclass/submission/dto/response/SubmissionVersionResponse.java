package com.codegym.mathclass.submission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionVersionResponse {
    private long id;
    private long submissionId;
    private int versionNumber;
    private String content;
    private Double score;
    private String teacherFeedback;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
