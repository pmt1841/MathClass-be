package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface AssignmentService {

    /**
     * Bước 1: Giáo viên tạo bài tập mới với trạng thái DRAFT.
     * Chưa giao cho lớp nào.
     */
    AssignmentResponse createAssignment(CreateAssignmentRequest request, long teacherId);

    /**
     * Bước 2: Giáo viên publish bài tập và chọn các lớp để giao.
     * Chuyển trạng thái bản nháp thành ARCHIVED, tạo các bản clone cho mỗi lớp.
     */
    void publishAssignment(long assignmentId, PublishAssignmentRequest request, long teacherId);

    /**
     * Giáo viên xóa bài tập.
     */
    void deleteAssignment(long assignmentId, long teacherId);

    /**
     * Lấy danh sách bài tập của một lớp (hỗ trợ lọc theo từ khóa và trạng thái).
     */
    Page<AssignmentResponse> getAssignmentsByClassCode(String classCode, long userId, String keyword,
            AssignmentStatus status, Pageable pageable);

    /**
     * Lấy danh sách bài tập theo người dùng hiện tại (Giáo viên/Học sinh) kèm bộ
     * lọc
     */
    Page<AssignmentResponse> getAssignmentsForCurrentUser(long userId, String role, String keyword, String classCode,
            AssignmentStatus status, Pageable pageable);

    /**
     * Giáo viên sửa bài tập nếu chưa có học sinh nộp bài.
     * - DRAFT: sửa title + description tự do.
     * - ARCHIVED: sửa title + description, đồng bộ sang tất cả PUBLISHED con.
     * - PUBLISHED: sửa title + description + deadline.
     * - DELETED: từ chối.
     */
    AssignmentResponse updateAssignment(long assignmentId, UpdateAssignmentRequest request, long teacherId);

    /**
     * Lấy chi tiết bài tập theo ID
     */
    AssignmentResponse getAssignmentById(long assignmentId, long userId, String role);

    AssignmentImageDto uploadImageForAssignment(MultipartFile file) throws IOException;
}
