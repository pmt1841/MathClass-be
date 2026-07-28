package com.codegym.mathclass.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SheetCompletedStudentResponse {
    private long studentId;
    private String studentName;
    private String studentEmail;
    private long completedExercisesCount;
    private int totalExercisesCount;
    private LocalDateTime latestSubmittedAt;
    private Double totalScore;
    private Long firstAssignmentId;
    private Long firstSubmissionId;
}
