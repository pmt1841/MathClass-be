package com.codegym.mathclass.classroom.dto;

import com.codegym.mathclass.classroom.entity.StudentRemark;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRemarkResponse {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long teacherId;
    private String teacherName;
    private String teacherAvatarUrl;
    private String strengths;
    private String weaknesses;
    private String generalAssessment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentRemarkResponse fromEntity(StudentRemark remark) {
        return StudentRemarkResponse.builder()
                .id(remark.getId())
                .studentId(remark.getStudent() != null ? remark.getStudent().getId() : null)
                .studentName(remark.getStudent() != null ? remark.getStudent().getFullName() : null)
                .teacherId(remark.getTeacher() != null ? remark.getTeacher().getId() : null)
                .teacherName(remark.getTeacher() != null ? remark.getTeacher().getFullName() : null)
                .teacherAvatarUrl(remark.getTeacher() != null ? remark.getTeacher().getAvatarUrl() : null)
                .strengths(remark.getStrengths())
                .weaknesses(remark.getWeaknesses())
                .generalAssessment(remark.getGeneralAssessment())
                .createdAt(remark.getCreatedAt())
                .updatedAt(remark.getUpdatedAt())
                .build();
    }
}
