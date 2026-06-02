package com.codegym.mathclass.classroom.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;

public interface ClassroomService {
    ClassroomResponse createClassroom(CreateClassroomRequest request, Long currentUserId);

    List<ClassroomResponse> getClassroomsListById(Long currentUserId);

    void addStudentToClass(String classCode, String studentEmail, Long teacherId);

    Page<StudentResponse> getStudentsByClassCode(String classCode, Long currentUserId, Pageable pageable);

    ClassroomResponse getClassroomByClassCode(String classCode, Long currentUserId);

    void removeStudentFromClass(String classCode, Long studentId, Long teacherId);

    ClassroomResponse updateClassroom(String classCode, UpdateClassroomRequest request, Long currentUserId);
}
