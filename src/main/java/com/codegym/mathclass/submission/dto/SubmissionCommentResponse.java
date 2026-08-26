package com.codegym.mathclass.submission.dto;

import com.codegym.mathclass.submission.entity.SubmissionComment;
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

    public static SubmissionCommentResponse fromEntity(SubmissionComment comment) {
        if (comment == null) {
            return null;
        }

        return SubmissionCommentResponse.builder()
                .id(comment.getId())
                .submissionId(comment.getSubmission().getId())
                .versionNumber(comment.getVersionNumber() != null ? comment.getVersionNumber() : 1)
                .teacherId(comment.getTeacher().getId())
                .teacherName(comment.getTeacher().getFullName())
                .quoteText(comment.getQuoteText())
                .occurrenceIndex(comment.getOccurrenceIndex())
                .imageCode(comment.getImageCode())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
