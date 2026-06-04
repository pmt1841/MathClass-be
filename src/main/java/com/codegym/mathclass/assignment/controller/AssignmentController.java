package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    /**
     * Bước 1: Giáo viên tạo bài tập mới (trạng thái DRAFT).
     * Chưa giao cho lớp nào.
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> createAssignment(
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            Long teacherId = userDetails.getId();
            AssignmentResponse response = assignmentService.createAssignment(request, teacherId);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            // Bắt lỗi từ validate LaTeX → trả 400 Bad Request
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Bước 2: Giáo viên publish bài tập và chọn các lớp để giao.
     * Chuyển trạng thái từ DRAFT → PUBLISHED.
     */
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> publishAssignment(
            @PathVariable Long id,
            @Valid @RequestBody PublishAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long teacherId = userDetails.getId();
        AssignmentResponse response = assignmentService.publishAssignment(id, request, teacherId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
