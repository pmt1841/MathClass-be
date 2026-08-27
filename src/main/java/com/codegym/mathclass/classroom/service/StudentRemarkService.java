package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.CreateStudentRemarkRequest;
import com.codegym.mathclass.classroom.dto.StudentRemarkResponse;

import java.util.List;

public interface StudentRemarkService {

    List<StudentRemarkResponse> getStudentRemarks(String classCode, Long studentId, Long currentUserId);

    StudentRemarkResponse createStudentRemark(String classCode, Long studentId, Long currentUserId, CreateStudentRemarkRequest request);

    void deleteStudentRemark(String classCode, Long studentId, Long remarkId, Long currentUserId);
}
