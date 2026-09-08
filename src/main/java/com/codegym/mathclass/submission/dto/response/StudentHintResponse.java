package com.codegym.mathclass.submission.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"stepIndex", "step", "hintNumber"})
    private Integer hintNumber;
    private Integer maxHints;
    private Integer remainingHints;
    @JsonAlias({"hintText", "hint", "content"})
    private String hintContent;
    private LocalDateTime createdAt;
}
