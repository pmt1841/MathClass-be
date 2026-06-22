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

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    // Nộp bài làm
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> createSubmission(
            @RequestBody SubmissionRequest requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.createSubmission(studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    // Cập nhật bài làm
    @PutMapping("/{submissionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> updateSubmission(
            @PathVariable long submissionId,
            @RequestBody SubmissionRequest requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.updateSubmission(submissionId, studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    // Hủy nộp
    @PutMapping("/{submissionId}/unsubmit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> unsubmitSubmission(
            @PathVariable long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long studentId = userDetails.getId();
        SubmissionResponse response = submissionService.unsubmitSubmission(submissionId, studentId);
        return ResponseEntity.ok(response);
    }

    // Chấm điểm
    @PutMapping("/{submissionId}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<SubmissionResponse> gradeSubmission(
            @PathVariable long submissionId,
            @Valid @RequestBody GradeRequest requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        SubmissionResponse response = submissionService.gradeSubmission(submissionId, teacherId, requestDto);
        return ResponseEntity.ok(response);
    }

    // Lấy bài làm theo id bài tập
    @GetMapping("/my-submission")
    @PreAuthorize("hasRole('STUDENT')")
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

    // Lấy danh sách bài làm theo id bài tập (dành cho giáo viên)
    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
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

    // Lấy chi tiết một bài nộp (dành cho giáo viên)
    @GetMapping("/{submissionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<SubmissionResponse> getSubmissionDetail(
            @PathVariable long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        SubmissionResponse response = submissionService.getSubmissionDetail(submissionId, teacherId);
        return ResponseEntity.ok(response);
    }
}
