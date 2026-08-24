package com.codegym.mathclass.classroom.dto;

import java.time.LocalDateTime;

import com.codegym.mathclass.classroom.entity.Classroom;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResponse {
    private long id;
    private String classCode;
    private String className;
    private long teacherId;
    private String teacherName;
    private String teacherEmail;
    private String teacherPhone;
    private String teacherAvatarUrl;
    private String teacherAvatar;
    private int studentCount;
    private Integer maxStudents;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        response.setTeacherEmail(classroom.getTeacher() != null ? classroom.getTeacher().getEmail() : null);
        response.setTeacherPhone(classroom.getTeacher() != null ? classroom.getTeacher().getPhoneNumber() : null);
        
        String avatar = classroom.getTeacher() != null ? classroom.getTeacher().getAvatarUrl() : null;
        response.setTeacherAvatarUrl(avatar);
        response.setTeacherAvatar(avatar);

        response.setStudentCount(classroom.getStudents() != null ? classroom.getStudents().size() : 0);
        response.setMaxStudents(classroom.getMaxStudents());
        response.setDescription(classroom.getDescription());
        response.setCreatedAt(classroom.getCreatedAt());
        response.setUpdatedAt(classroom.getUpdatedAt());
        return response;
    }
}
