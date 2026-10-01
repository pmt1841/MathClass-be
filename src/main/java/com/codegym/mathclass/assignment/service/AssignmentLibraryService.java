package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Dịch vụ chuyên trách quản lý Thư viện dùng chung (Thư viện bài tập và phiếu bài tập công khai).
 */
public interface AssignmentLibraryService {

    Page<AssignmentResponse> getPublicAssignments(String keyword, Pageable pageable);

    AssignmentResponse getPublicAssignmentDetail(long assignmentId);

    AssignmentResponse cloneAssignmentFromLibrary(long assignmentId, long teacherId);

    Page<AssignmentSheetResponse> getPublicAssignmentSheets(String keyword, Pageable pageable);

    AssignmentSheetResponse cloneAssignmentSheetFromLibrary(long sheetId, long teacherId);
}
