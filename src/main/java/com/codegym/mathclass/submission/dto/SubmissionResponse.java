package com.codegym.mathclass.submission.dto;

import com.codegym.mathclass.submission.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.codegym.mathclass.submission.entity.Submission;

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

    public static SubmissionResponse fromEntity(Submission submission) {
        if (submission == null) {
            return null;
        }

        return SubmissionResponse.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignment() != null ? submission.getAssignment().getId() : 0)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : 0)
                .studentName(submission.getStudent() != null ? submission.getStudent().getFullName() : null)
                .content(submission.getContent())
                .teacherFeedback(submission.getTeacherFeedback())
                .status(submission.getStatus())
                .score(submission.getScore())
                .submittedAt(submission.getSubmittedAt())
                .updatedAt(submission.getUpdatedAt())
                .allowResubmit(submission.getAssignment() != null ? submission.getAssignment().isAllowResubmit() : false)
                .build();
    }
}
