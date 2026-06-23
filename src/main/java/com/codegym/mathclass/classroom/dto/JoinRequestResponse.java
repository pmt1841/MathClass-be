package com.codegym.mathclass.classroom.dto;

import com.codegym.mathclass.classroom.entity.ClassroomJoinRequest;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JoinRequestResponse {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private String classCode;
    private String className;
    private JoinRequestStatus status;
    private LocalDateTime requestedAt;

    public static JoinRequestResponse fromEntity(ClassroomJoinRequest request) {
        return JoinRequestResponse.builder()
                .id(request.getId())
                .studentId(request.getStudent().getId())
                .studentName(request.getStudent().getFullName())
                .studentEmail(request.getStudent().getEmail())
                .classCode(request.getClassroom().getClassCode())
                .className(request.getClassroom().getClassName())
                .status(request.getStatus())
                .requestedAt(request.getCreatedAt())
                .build();
    }
}
