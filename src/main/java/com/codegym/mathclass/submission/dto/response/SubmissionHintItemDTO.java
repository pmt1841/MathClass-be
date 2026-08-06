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
public class SubmissionHintItemDTO {
    private Long id;
    private Integer hintNumber;
    private String studentSnapshotContent;
    private String aiHintContent;
    private LocalDateTime createdAt;
}
