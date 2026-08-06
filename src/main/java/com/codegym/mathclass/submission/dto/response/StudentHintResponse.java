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
public class StudentHintResponse {
    private Long id;
    private Long submissionId;
    private Integer hintNumber;
    private Integer maxHints;
    private Integer remainingHints;
    private String hintContent;
    private LocalDateTime createdAt;
}
