package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import org.springframework.security.core.GrantedAuthority;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    /**
     * Giáo viên tạo bài tập mới (trạng thái DRAFT).
     * Chưa giao cho lớp nào.
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> createAssignment(
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            long teacherId = userDetails.getId();
            AssignmentResponse response = assignmentService.createAssignment(request, teacherId);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            // Bắt lỗi từ validate LaTeX → trả 400 Bad Request
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Giáo viên publish bài tập và chọn các lớp để giao.
     * Chuyển trạng thái từ DRAFT → PUBLISHED.
     */
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> publishAssignment(
            @PathVariable long id,
            @Valid @RequestBody PublishAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        assignmentService.publishAssignment(id, request, teacherId);
        return ResponseEntity.ok().build();
    }

    /**
     * Giáo viên xóa bài tập.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> deleteAssignment(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        assignmentService.deleteAssignment(id, teacherId);
        return ResponseEntity.ok().build();
    }

    /**
     * 
     * Lấy danh sách bài tập theo người dùng hiện tại (Giáo viên hoặc Học sinh).
     * Hỗ trợ tìm kiếm theo từ khóa (tiêu đề), lọc theo mã lớp, lọc theo trạng thái
     * và phân trang.
     */
    @GetMapping
    public ResponseEntity<Page<AssignmentResponse>> getAllAssignmentsForCurrentUser(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String classCode,
            @RequestParam(required = false) AssignmentStatus status,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        // Lấy role đầu tiên (TEACHER hoặc STUDENT)
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        Page<AssignmentResponse> assignments = assignmentService.getAssignmentsForCurrentUser(
                userId, role, keyword, classCode, status, pageable);

        return ResponseEntity.ok(assignments);
    }

    /**
     * Lấy chi tiết bài tập theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponse> getAssignmentById(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        AssignmentResponse response = assignmentService.getAssignmentById(id, userId, role);
        return ResponseEntity.ok(response);
    }

    /**
     * Giáo viên sửa bài tập nếu chưa có học sinh nộp bài.
     * - DRAFT: sửa title + description tự do.
     * - ARCHIVED: sửa title + description, đồng bộ sang tất cả PUBLISHED con.
     * - PUBLISHED: sửa title + description + deadline.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> updateAssignment(
            @PathVariable long id,
            @Valid @RequestBody UpdateAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            long teacherId = userDetails.getId();
            AssignmentResponse response = assignmentService.updateAssignment(id, request, teacherId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // Bắt lỗi từ validate LaTeX → trả 400 Bad Request
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/images/upload")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            AssignmentImageDto imageDto = assignmentService.uploadImageForAssignment(file);
            return ResponseEntity.ok(imageDto);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi upload ảnh: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
