package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.GradeRequestDto;
import com.codegym.mathclass.submission.dto.SubmissionRequestDto;
import com.codegym.mathclass.submission.dto.SubmissionResponseDto;
import com.codegym.mathclass.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> createSubmission(
            @RequestBody SubmissionRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        long studentId = userDetails.getId();
        SubmissionResponseDto response = submissionService.createSubmission(studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{submissionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> updateSubmission(
            @PathVariable long submissionId,
            @RequestBody SubmissionRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        long studentId = userDetails.getId();
        SubmissionResponseDto response = submissionService.updateSubmission(submissionId, studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{submissionId}/unsubmit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> unsubmitSubmission(
            @PathVariable long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        long studentId = userDetails.getId();
        SubmissionResponseDto response = submissionService.unsubmitSubmission(submissionId, studentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{submissionId}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<SubmissionResponseDto> gradeSubmission(
            @PathVariable long submissionId,
            @RequestBody GradeRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        long teacherId = userDetails.getId();
        SubmissionResponseDto response = submissionService.gradeSubmission(submissionId, teacherId, requestDto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-submission")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> getMySubmission(
            @RequestParam long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
            
        long studentId = userDetails.getId();
        SubmissionResponseDto response = submissionService.getMySubmission(assignmentId, studentId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<SubmissionResponseDto>> getSubmissionsByAssignment(
            @RequestParam long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
            
        long teacherId = userDetails.getId();
        List<SubmissionResponseDto> responses = submissionService.getSubmissionsByAssignment(assignmentId, teacherId);
        return ResponseEntity.ok(responses);
    }
}
