package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.request.StudentHintRequest;
import com.codegym.mathclass.submission.dto.response.HintHistoryResponse;
import com.codegym.mathclass.submission.dto.response.StudentHintResponse;
import com.codegym.mathclass.submission.service.AiHintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Student AI Hints", description = "APIs gợi ý tư duy từng bước cho học sinh và tra cứu lịch sử gợi ý")
@RestController
@ApiVersion(1)
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class StudentHintController {

    private final AiHintService aiHintService;

    @Operation(summary = "Yêu cầu AI gợi ý tư duy từng bước", description = "Học sinh bấm xin gợi ý tư duy kế tiếp khi làm bài tập (tối đa 3 gợi ý/bài tập)")
    @PostMapping("/assignments/{assignmentId}/hints")
    @PreAuthorize("hasAnyAuthority('submission:submit', 'submission:read_own')")
    public ResponseEntity<StudentHintResponse> requestHint(
            @PathVariable Long assignmentId,
            @RequestBody(required = false) StudentHintRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        StudentHintResponse response = aiHintService.requestHint(assignmentId, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy lịch sử các lượt gợi ý của bài nộp", description = "Lấy toàn bộ lịch sử các lần xin gợi ý AI của bài nộp")
    @GetMapping("/{submissionId}/hints")
    @PreAuthorize("hasAnyAuthority('submission:read_own', 'submission:read_all')")
    public ResponseEntity<HintHistoryResponse> getHintHistory(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        HintHistoryResponse response = aiHintService.getHintHistory(submissionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
