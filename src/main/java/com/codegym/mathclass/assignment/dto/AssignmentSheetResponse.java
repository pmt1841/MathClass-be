package com.codegym.mathclass.assignment.dto;

import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class AssignmentSheetResponse {
    private long id;
    private String type = "SHEET";
    private String title;
    private String description;
    private LocalDateTime deadline;
    private com.codegym.mathclass.assignment.entity.AssignmentVisibility visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    private long teacherId;
    private String teacherName;
    private Long originalAuthorId;
    private String originalAuthorName;
    private String classCode;
    private String className;

    private List<AssignmentResponse> items;
    private String submissionStatus;
    private boolean hasSubmissions;
    private List<String> publishedClassCodes;

    public static AssignmentSheetResponse fromEntity(AssignmentSheet sheet) {
        if (sheet == null) return null;
        
        AssignmentSheetResponse res = new AssignmentSheetResponse();
        res.setId(sheet.getId());
        res.setTitle(sheet.getTitle());
        res.setDescription(sheet.getDescription());
        res.setDeadline(sheet.getDeadline());
        res.setVisibility(sheet.getVisibility());
        res.setCreatedAt(sheet.getCreatedAt());
        res.setUpdatedAt(sheet.getUpdatedAt());
        
        if (sheet.getTeacher() != null) {
            res.setTeacherId(sheet.getTeacher().getId());
            res.setTeacherName(sheet.getTeacher().getFullName());
        }

        if (sheet.getOriginalAuthor() != null) {
            res.setOriginalAuthorId(sheet.getOriginalAuthor().getId());
            res.setOriginalAuthorName(sheet.getOriginalAuthor().getFullName());
        }
        
        if (sheet.getClassroom() != null) {
            res.setClassCode(sheet.getClassroom().getClassCode());
            res.setClassName(sheet.getClassroom().getClassName());
        }
        
        if (sheet.getItems() != null) {
            res.setItems(sheet.getItems().stream()
                .filter(item -> item.getAssignment() != null && item.getAssignment().getStatus() != com.codegym.mathclass.assignment.entity.AssignmentStatus.DELETED)
                .map(item -> AssignmentResponse.fromEntityWithoutContent(item.getAssignment()))
                .collect(Collectors.toList()));
        }
        return res;
    }
}
