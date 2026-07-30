package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.service.SubmissionDrawingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Submission Drawings", description = "APIs lưu trữ và xem hình vẽ/ghi chú trực quan trên bài nộp của học sinh")
@RestController
@ApiVersion(1)
@RequestMapping("/submissions/{submissionId}/drawings")
@RequiredArgsConstructor
public class SubmissionDrawingController {

    private final SubmissionDrawingService submissionDrawingService;

    @Operation(summary = "Lưu hoặc cập nhật hình vẽ ghi chú", description = "Lưu canvas hình vẽ/nội dung vẽ tay trên bài nộp")
    @PutMapping
    public ResponseEntity<SubmissionDrawingResponse> saveOrUpdateDrawing(
            @PathVariable long submissionId,
            @Valid @RequestBody SubmissionDrawingRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        SubmissionDrawingResponse response = submissionDrawingService.saveOrUpdateDrawing(submissionId, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy hình vẽ ghi chú của bài nộp", description = "Lấy dữ liệu hình vẽ canvas đã lưu trên bài nộp")
    @GetMapping
    public ResponseEntity<SubmissionDrawingResponse> getDrawing(
            @PathVariable long submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        SubmissionDrawingResponse response = submissionDrawingService.getDrawingBySubmissionId(submissionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
