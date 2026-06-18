package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.service.SubmissionDrawingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions/{submissionId}/drawings")
@RequiredArgsConstructor
public class SubmissionDrawingController {

    private final SubmissionDrawingService submissionDrawingService;

    @PutMapping
    public ResponseEntity<Map<String, Object>> saveOrUpdateDrawing(
            @PathVariable long submissionId,
            @Valid @RequestBody SubmissionDrawingRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        SubmissionDrawingResponse response = submissionDrawingService.saveOrUpdateDrawing(submissionId, request, userDetails.getUsername());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "Drawing saved successfully");
        responseBody.put("data", response);

        return ResponseEntity.ok(responseBody);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDrawing(
            @PathVariable long submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        SubmissionDrawingResponse response = submissionDrawingService.getDrawingBySubmissionId(submissionId, userDetails.getUsername());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", true);
        responseBody.put("data", response);

        return ResponseEntity.ok(responseBody);
    }
}
