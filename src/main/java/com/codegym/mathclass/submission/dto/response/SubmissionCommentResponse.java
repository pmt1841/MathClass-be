package com.codegym.mathclass.submission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionCommentResponse {

    private Long id;
    private Long submissionId;
    private Integer versionNumber;
    private Long teacherId;
    private String teacherName;
    private String quoteText;
    private Integer occurrenceIndex;
    private String imageCode;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
