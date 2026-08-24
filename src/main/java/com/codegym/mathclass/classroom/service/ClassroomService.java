package com.codegym.mathclass.classroom.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;

public interface ClassroomService {
    ClassroomResponse createClassroom(CreateClassroomRequest request, long currentUserId);

    List<ClassroomResponse> getClassroomsListById(long currentUserId);

    void addStudentToClass(String classCode, String studentEmail, long teacherId);

    Page<StudentResponse> getStudentsByClassCode(String classCode, long currentUserId, String keyword, Pageable pageable);

    ClassroomResponse getClassroomByClassCode(String classCode, long currentUserId);

    void removeStudentFromClass(String classCode, long studentId, long teacherId);

    ClassroomResponse updateClassroom(String classCode, UpdateClassroomRequest request, long currentUserId);

    void deleteClassroom(String classCode, long currentUserId);
}
