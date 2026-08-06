package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.CreateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.PublishAssignmentRequest;
import com.codegym.mathclass.assignment.dto.UpdateAssignmentRequest;
import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
import com.codegym.mathclass.assignment.dto.TextExtractionResponse;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.codegym.mathclass.assignment.dto.UpdateVisibilityRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import org.springframework.security.core.GrantedAuthority;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Assignments", description = "APIs quản lý bài tập (Tạo nháp, xuất bản, chỉnh sửa, xóa, tìm kiếm, upload ảnh, trích xuất text PDF/DOCX)")
@RestController
@ApiVersion(1)
@RequestMapping("/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Operation(summary = "Tạo bài tập mới (DRAFT)", description = "Giáo viên tạo bài tập nháp mới, hỗ trợ công thức toán LaTeX")
    @PostMapping
    @PreAuthorize("hasAuthority('assignment:create')")
    public ResponseEntity<AssignmentResponse> createAssignment(
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        AssignmentResponse response = assignmentService.createAssignment(request, teacherId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Giao bài tập cho các lớp (Publish)", description = "Chuyển trạng thái từ DRAFT sang PUBLISHED và chọn danh sách các lớp để giao bài")
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('assignment:publish')")
    public ResponseEntity<Void> publishAssignment(
            @PathVariable long id,
            @Valid @RequestBody PublishAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        assignmentService.publishAssignment(id, request, teacherId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Xóa bài tập", description = "Giáo viên xóa bài tập theo ID")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:delete')")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        assignmentService.deleteAssignment(id, teacherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Danh sách bài tập của người dùng", description = "Lấy danh sách bài tập của Giáo viên/Học sinh, hỗ trợ lọc từ khóa, mã lớp, trạng thái và phân trang")
    @GetMapping
    public ResponseEntity<Page<AssignmentResponse>> getAllAssignmentsForCurrentUser(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String classCode,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) Long gradeTagId,
            @RequestParam(required = false) Long subjectTagId,
            @RequestParam(required = false) Long difficultyTagId,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        Page<AssignmentResponse> assignments = assignmentService.getAssignmentsForCurrentUser(
                userId, role, keyword, classCode, status, gradeTagId, subjectTagId, difficultyTagId, pageable);

        return ResponseEntity.ok(assignments);
    }

    @Operation(summary = "Chi tiết bài tập theo ID", description = "Lấy chi tiết đề bài tập, danh sách câu hỏi và tài liệu đính kèm")
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

    @Operation(summary = "Cập nhật bài tập", description = "Chỉnh sửa nội dung bài tập, tiêu đề, mô tả hoặc thời hạn nộp bài")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:update')")
    public ResponseEntity<AssignmentResponse> updateAssignment(
            @PathVariable long id,
            @Valid @RequestBody UpdateAssignmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        AssignmentResponse response = assignmentService.updateAssignment(id, request, teacherId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cập nhật Visibility bài tập", description = "Chuyển trạng thái bài tập giữa PRIVATE và PUBLIC (hiển thị trong Thư viện dùng chung)")
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAuthority('assignment:update')")
    public ResponseEntity<?> updateAssignmentVisibility(
            @PathVariable long id,
            @Valid @RequestBody UpdateVisibilityRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            long teacherId = userDetails.getId();
            AssignmentResponse response = assignmentService.updateAssignmentVisibility(id, request, teacherId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Tải lên hình ảnh bài tập", description = "Upload hình ảnh minh họa cho câu hỏi bài tập toán")
    @PostMapping("/images")
    @PreAuthorize("hasAuthority('assignment:create')")
    public ResponseEntity<AssignmentImageDto> uploadImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {

        AssignmentImageDto imageDto = assignmentService.uploadImageForAssignment(file);
        return ResponseEntity.ok(imageDto);
    }

    @Operation(summary = "Trích xuất văn bản từ tài liệu (PDF/DOCX)", description = "Trích xuất nội dung đề bài và câu hỏi từ file PDF hoặc DOCX")
    @PostMapping("/extract-text")
    @PreAuthorize("hasAuthority('assignment:create')")
    public ResponseEntity<TextExtractionResponse> extractTextFromFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {

        java.util.Map<String, Object> result = assignmentService.extractTextFromFile(file);
        return ResponseEntity.ok(TextExtractionResponse.builder().data(result).build());
    }
}
