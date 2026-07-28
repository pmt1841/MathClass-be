package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;

public interface AssignmentSheetService {
    void publishAssignmentSheet(PublishAssignmentSheetRequest request, long teacherId);
    Page<AssignmentSheetResponse> getAssignmentSheetsForCurrentUser(long userId, String role, String keyword, String classCode, Pageable pageable);
    void deleteAssignmentSheet(long sheetId, long teacherId);
    AssignmentSheetResponse updateAssignmentSheet(long sheetId, UpdateAssignmentSheetRequest request, long teacherId);
    Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable);
    AssignmentSheetResponse cloneAssignmentSheetFromLibrary(long sheetId, long teacherId);
    Page<com.codegym.mathclass.assignment.dto.SheetCompletedStudentResponse> getCompletedStudentsBySheet(long sheetId, String classCode, Pageable pageable, long teacherId);
}
