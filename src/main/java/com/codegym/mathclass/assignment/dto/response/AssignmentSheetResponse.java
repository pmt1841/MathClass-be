package com.codegym.mathclass.assignment.dto.response;

import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentSheetResponse {
    private long id;
    private String type = "SHEET";
    private String title;
    private String description;
    private LocalDateTime deadline;
    private AssignmentVisibility visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private long teacherId;
    private String teacherName;
    private Long originalAuthorId;
    private String originalAuthorName;
    private String classCode;
    private String className;
    private Long masterSheetId;

    private List<AssignmentResponse> items;
    private String submissionStatus;
    private LocalDateTime submissionCreatedAt;
    private LocalDateTime submissionUpdatedAt;
    private boolean hasSubmissions;
    private List<String> publishedClassCodes;
}
