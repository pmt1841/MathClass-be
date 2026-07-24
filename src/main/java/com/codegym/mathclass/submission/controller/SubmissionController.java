package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Submissions", description = "APIs quản lý bài làm của học sinh (Nộp bài, sửa bài làm, hủy nộp, chấm điểm, truy vấn bài nộp)")
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(summary = "Nộp bài làm", description = "Học sinh nộp câu trả lời bài tập (tự luận / trắc nghiệm)")
    @PostMapping
    @PreAuthorize("hasAuthority('submission:submit')")
    public ResponseEntity<SubmissionResponse> createSubmission(
            @RequestBody SubmissionRequest requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.createSubmission(studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cập nhật bài làm", description = "Học sinh chỉnh sửa nội dung bài làm trước khi hết hạn hoặc trước khi giáo viên chấm điểm")
    @PutMapping("/{submissionId}")
    @PreAuthorize("hasAuthority('submission:submit')")
    public ResponseEntity<SubmissionResponse> updateSubmission(
            @PathVariable long submissionId,
            @RequestBody SubmissionRequest requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.updateSubmission(submissionId, studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Hủy nộp bài", description = "Học sinh rút lại bài làm để chỉnh sửa lại")
    @PutMapping("/{submissionId}/unsubmit")
    @PreAuthorize("hasAuthority('submission:submit')")
    public ResponseEntity<SubmissionResponse> unsubmitSubmission(
            @PathVariable long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.unsubmitSubmission(submissionId, studentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Chấm điểm bài làm", description = "Giáo viên nhập điểm và nhận xét cho bài nộp của học sinh")
    @PutMapping("/{submissionId}/grade")
    @PreAuthorize("hasAuthority('submission:grade')")
    public ResponseEntity<SubmissionResponse> gradeSubmission(
            @PathVariable long submissionId,
            @Valid @RequestBody GradeRequest requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        SubmissionResponse response = submissionService.gradeSubmission(submissionId, teacherId, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy bài làm cá nhân của học sinh", description = "Học sinh truy vấn bài làm của chính mình cho một bài tập")
    @GetMapping("/my-submission")
    @PreAuthorize("hasAuthority('submission:read_own')")
    public ResponseEntity<SubmissionResponse> getMySubmission(
            @RequestParam long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.getMySubmission(assignmentId, studentId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Danh sách bài nộp của bài tập (Dành cho Giáo viên)", description = "Giáo viên truy vấn danh sách tất cả các bài nộp của bài tập, hỗ trợ lọc theo trạng thái và tìm kiếm học sinh")
    @GetMapping
    @PreAuthorize("hasAuthority('submission:read_all')")
    public ResponseEntity<Page<SubmissionResponse>> getSubmissionsByAssignment(
            @RequestParam long assignmentId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        Page<SubmissionResponse> responses = submissionService
                .getSubmissionsByAssignment(
                        assignmentId, teacherId, status, keyword, pageable);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Chi tiết một bài nộp (Dành cho Giáo viên)", description = "Giáo viên xem chi tiết bài nộp và kết quả trả lời của một học sinh")
    @GetMapping("/{submissionId}")
    @PreAuthorize("hasAuthority('submission:read_all')")
    public ResponseEntity<SubmissionResponse> getSubmissionDetail(
            @PathVariable long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        SubmissionResponse response = submissionService.getSubmissionDetail(submissionId, teacherId);
        return ResponseEntity.ok(response);
    }
}
