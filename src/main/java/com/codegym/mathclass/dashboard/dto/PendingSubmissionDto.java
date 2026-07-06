package com.codegym.mathclass.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingSubmissionDto {
    private Long id;
    private Long assignmentId;
    private String studentName;
    private String assignmentTitle;
    private String className;
    private String classCode;
    private LocalDateTime submittedAt;
}
