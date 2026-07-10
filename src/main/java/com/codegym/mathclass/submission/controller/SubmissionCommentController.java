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

@RestController
@RequestMapping("/api/submissions/{submissionId}/comments")
@RequiredArgsConstructor
public class SubmissionCommentController {

    private final SubmissionCommentService submissionCommentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    public ResponseEntity<List<SubmissionCommentResponse>> getCommentsBySubmissionId(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        List<SubmissionCommentResponse> responses = submissionCommentService.getCommentsBySubmissionId(submissionId, userDetails.getUsername());
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<SubmissionCommentResponse> addComment(
            @PathVariable Long submissionId,
            @Valid @RequestBody SubmissionCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Long teacherId = userDetails.getId();
        SubmissionCommentResponse response = submissionCommentService.addComment(submissionId, teacherId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long submissionId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Long teacherId = userDetails.getId();
        submissionCommentService.deleteComment(submissionId, commentId, teacherId);
        return ResponseEntity.noContent().build();
    }
}
