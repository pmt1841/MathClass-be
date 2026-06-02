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
    private int studentCount;
    private Integer maxStudents;
    private String description;

    public static ClassroomResponse fromEntity(Classroom classroom) {
        if (classroom == null) {
            return null;
        }
        ClassroomResponse response = new ClassroomResponse();
        response.setId(classroom.getId());
        response.setClassCode(classroom.getClassCode());
        response.setClassName(classroom.getClassName());
        response.setTeacherId(classroom.getTeacher() != null ? classroom.getTeacher().getId() : null);
        response.setTeacherName(classroom.getTeacher() != null ? classroom.getTeacher().getFullName() : null);
        response.setStudentCount(classroom.getStudents() != null ? classroom.getStudents().size() : 0);
        response.setMaxStudents(classroom.getMaxStudents());
        response.setDescription(classroom.getDescription());
        return response;
    }
}
