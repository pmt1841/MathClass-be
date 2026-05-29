package com.codegym.mathclass.classroom.dto;

import com.codegym.mathclass.classroom.entity.Classroom;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResponse {
    private Long id;
    private String classCode;
    private String className;
    private Long teacherId;
    private String teacherName;

    public static ClassroomResponse fromEntity(Classroom classroom) {
        if (classroom == null) {
            return null;
        }
        return new ClassroomResponse(
                classroom.getId(),
                classroom.getClassCode(),
                classroom.getClassName(),
                classroom.getTeacher() != null ? classroom.getTeacher().getId() : null,
                classroom.getTeacher() != null ? classroom.getTeacher().getFullName() : null);
    }
}
