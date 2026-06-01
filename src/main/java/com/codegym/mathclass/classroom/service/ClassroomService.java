package com.codegym.mathclass.classroom.service;

import java.util.List;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;

public interface ClassroomService {
    ClassroomResponse createClassroom(CreateClassroomRequest request, Long currentUserId);

    List<ClassroomResponse> getClassroomsListById(Long currentUserId);

    void addStudentToClass(String classCode, String studentEmail, Long teacherId);

    List<StudentResponse> getStudentsByClassCode(String classCode, Long currentUserId);

    ClassroomResponse getClassroomByClassCode(String classCode, Long currentUserId);
}
