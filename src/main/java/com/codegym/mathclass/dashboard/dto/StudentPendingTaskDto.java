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
public class StudentPendingTaskDto {
    private long id;
    private String title;
    private String classCode;
    private String className;
    private LocalDateTime deadline;
    private String type; // e.g., assignment subject/type
}
