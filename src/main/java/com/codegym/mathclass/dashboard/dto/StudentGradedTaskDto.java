package com.codegym.mathclass.dashboard.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentGradedTaskDto {
    private long id;
    private String title;
    private String classCode;
    private String className;
    private LocalDateTime gradedAt;
    private Float score;
    private Float maxScore;
    private String teacherComment;
}
