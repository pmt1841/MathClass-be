package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.SubmissionCommentRequest;
import com.codegym.mathclass.submission.dto.SubmissionCommentResponse;
import com.codegym.mathclass.submission.service.SubmissionCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Submission Comments", description = "APIs bình luận và nhận xét cho bài nộp của học sinh")
@RestController
@RequestMapping("/api/submissions/{submissionId}/comments")
@RequiredArgsConstructor
public class SubmissionCommentController {

    private final SubmissionCommentService submissionCommentService;

    @Operation(summary = "Danh sách bình luận của bài nộp", description = "Lấy danh sách các nhận xét/bình luận của bài nộp")
    @GetMapping
    @PreAuthorize("hasAuthority('submission:comment')")
    public ResponseEntity<List<SubmissionCommentResponse>> getCommentsBySubmissionId(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        List<SubmissionCommentResponse> responses = submissionCommentService.getCommentsBySubmissionId(submissionId, userDetails.getUsername());
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Thêm bình luận / nhận xét mới", description = "Giáo viên hoặc học sinh gửi nhận xét mới cho bài nộp")
    @PostMapping
    @PreAuthorize("hasAuthority('submission:comment')")
    public ResponseEntity<SubmissionCommentResponse> addComment(
            @PathVariable Long submissionId,
            @Valid @RequestBody SubmissionCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Long teacherId = userDetails.getId();
        SubmissionCommentResponse response = submissionCommentService.addComment(submissionId, teacherId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Xóa bình luận", description = "Xóa một bình luận đã gửi theo ID bình luận")
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAuthority('submission:comment')")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long submissionId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Long teacherId = userDetails.getId();
        submissionCommentService.deleteComment(submissionId, commentId, teacherId);
        return ResponseEntity.noContent().build();
    }
}
