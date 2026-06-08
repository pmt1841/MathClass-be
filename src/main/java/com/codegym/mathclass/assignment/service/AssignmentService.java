package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssignmentService {

    /**
     * Bước 1: Giáo viên tạo bài tập mới với trạng thái DRAFT.
     * Chưa giao cho lớp nào.
     */
    AssignmentResponse createAssignment(CreateAssignmentRequest request, Long teacherId);

    /**
     * Bước 2: Giáo viên publish bài tập và chọn các lớp để giao.
     * Chuyển trạng thái bản nháp thành ARCHIVED, tạo các bản clone cho mỗi lớp.
     */
    void publishAssignment(Long assignmentId, PublishAssignmentRequest request, Long teacherId);

    /**
     * Giáo viên xóa bài tập.
     */
    void deleteAssignment(Long assignmentId, Long teacherId);

    /**
     * Lấy danh sách bài tập của một lớp (hỗ trợ lọc theo từ khóa và trạng thái).
     */
    Page<AssignmentResponse> getAssignmentsByClassCode(String classCode, Long userId, String keyword, com.codegym.mathclass.assignment.entity.AssignmentStatus status, Pageable pageable);

    /**
     * Lấy danh sách bài tập theo người dùng hiện tại (Giáo viên/Học sinh) kèm bộ lọc
     */
    Page<AssignmentResponse> getAssignmentsForCurrentUser(Long userId, String role, String keyword, String classCode, com.codegym.mathclass.assignment.entity.AssignmentStatus status, Pageable pageable);

    /**
     * Giáo viên sửa bài tập nếu chưa có học sinh nộp bài.
     * - DRAFT: sửa title + description tự do.
     * - ARCHIVED: sửa title + description, đồng bộ sang tất cả PUBLISHED con.
     * - PUBLISHED: sửa title + description + deadline.
     * - DELETED: từ chối.
     */
    AssignmentResponse updateAssignment(Long assignmentId, UpdateAssignmentRequest request, Long teacherId);

    /**
     * Lấy chi tiết bài tập theo ID
     */
    AssignmentResponse getAssignmentById(Long assignmentId, Long userId, String role);
}
