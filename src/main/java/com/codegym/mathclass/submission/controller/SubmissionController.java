package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
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
@RequestMapping("/api/assignments/{assignmentId}/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> createSubmission(
            @PathVariable long assignmentId,
            @RequestBody SubmissionRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        long studentId = userDetails.getId();
        SubmissionResponseDto response = submissionService.saveSubmission(assignmentId, studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/my-submission")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> updateMySubmission(
            @PathVariable long assignmentId,
            @RequestBody SubmissionRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        long studentId = userDetails.getId();
        // create and update sharing the same logic: saveSubmission
        SubmissionResponseDto response = submissionService.saveSubmission(assignmentId, studentId, requestDto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-submission")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponseDto> getMySubmission(
            @PathVariable long assignmentId,
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
            @PathVariable long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
            
        long teacherId = userDetails.getId();
        List<SubmissionResponseDto> responses = submissionService.getSubmissionsByAssignment(assignmentId, teacherId);
        return ResponseEntity.ok(responses);
    }
}
