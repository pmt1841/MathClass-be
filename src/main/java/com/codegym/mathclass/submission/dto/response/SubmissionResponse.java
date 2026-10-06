package com.codegym.mathclass.submission.dto.response;

import com.codegym.mathclass.submission.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {
    private long id;
    private long assignmentId;
    private long studentId;
    private String studentName; 
    private String content;
    private String teacherFeedback;
    private SubmissionStatus status;
    private Double score;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private Integer versionNumber;
    private Integer totalVersions;
    private Boolean allowResubmit;
}
