package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;

public interface ClassroomService {
    ClassroomResponse createClassroom(CreateClassroomRequest request, String currentUserEmail);
}
