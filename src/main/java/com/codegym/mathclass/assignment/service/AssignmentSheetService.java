package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.UpdateVisibilityRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.mathclass.assignment.dto.SheetCompletedStudentResponse;

import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;

public interface AssignmentSheetService {
    void publishAssignmentSheet(PublishAssignmentSheetRequest request, long teacherId);
    Page<AssignmentSheetResponse> getAssignmentSheetsForCurrentUser(long userId, String role, String keyword, String classCode, String studentStatus, Pageable pageable);
    void deleteAssignmentSheet(long sheetId, long teacherId);
    AssignmentSheetResponse updateAssignmentSheet(long sheetId, UpdateAssignmentSheetRequest request, long teacherId);
    Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable);
    AssignmentSheetResponse cloneAssignmentSheetFromLibrary(long sheetId, long teacherId);
    Page<SheetCompletedStudentResponse> getCompletedStudentsBySheet(long sheetId, String classCode, Pageable pageable, long teacherId);

    /**
     * Cập nhật trạng thái Visibility (PRIVATE | PUBLIC) của phiếu bài tập.
     * Chỉ chủ sở hữu (teacher) mới được phép.
     */
    AssignmentSheetResponse updateAssignmentSheetVisibility(long sheetId, UpdateVisibilityRequest request, long teacherId);
}
