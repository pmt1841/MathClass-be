package com.codegym.mathclass.submission.dto;

import com.codegym.mathclass.submission.entity.SubmissionVersion;
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

    public static SubmissionVersionResponse fromEntity(SubmissionVersion version) {
        if (version == null) return null;
        return SubmissionVersionResponse.builder()
                .id(version.getId())
                .submissionId(version.getSubmission() != null ? version.getSubmission().getId() : 0)
                .versionNumber(version.getVersionNumber())
                .content(version.getContent())
                .score(version.getScore())
                .teacherFeedback(version.getTeacherFeedback())
                .submittedAt(version.getSubmittedAt())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
