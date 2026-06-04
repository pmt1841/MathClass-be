package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;

public interface AssignmentService {

    /**
     * Bước 1: Giáo viên tạo bài tập mới với trạng thái DRAFT.
     * Chưa giao cho lớp nào.
     */
    AssignmentResponse createAssignment(CreateAssignmentRequest request, Long teacherId);

    /**
     * Bước 2: Giáo viên publish bài tập và chọn các lớp để giao.
     * Chuyển trạng thái từ DRAFT → PUBLISHED.
     */
    AssignmentResponse publishAssignment(Long assignmentId, PublishAssignmentRequest request, Long teacherId);
}
