package com.codegym.mathclass.assignment.dto;

import java.time.LocalDateTime;

public interface UnifiedAssignmentDto {
    Long getId();
    String getType(); // 'ASSIGNMENT' or 'SHEET'
    String getTitle();
    String getDescription();
    LocalDateTime getDeadline();
    String getStatus();
    Long getTeacherId();
    String getTeacherName();
    String getClassroomCode();
    String getClassroomName();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    String getSubmissionStatus(); // For student
    Long getSubmissionId(); // For student
}
