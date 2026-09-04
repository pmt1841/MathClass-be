package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.aiqueue.dto.AiJobSubmitResponse;
import com.codegym.mathclass.aiqueue.dto.payload.AiGradingJobPayload;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.codegym.mathclass.submission.service.AiGradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Grading", description = "APIs AI chấm sơ bộ bài làm (đối chiếu hình vẽ Canvas + chấm tự luận) cho giáo viên")
@RestController
@ApiVersion(1)
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class AiGradingController {

    private final AiGradingService aiGradingService;
    private final AiJobService aiJobService;

    @Operation(summary = "AI chấm sơ bộ bài làm",
            description = "Giáo viên bấm AI chấm sơ bộ: AI đối chiếu hình vẽ Canvas của học sinh với hình mẫu, "
                    + "chấm điểm dự kiến và viết dự thảo nhận xét. Hỗ trợ async=true để đưa vào hàng đợi Redis")
    @PostMapping("/{submissionId}/ai-grading")
    @PreAuthorize("hasAuthority('submission:grade')")
    public ResponseEntity<?> requestAiGrading(
            @PathVariable long submissionId,
            @RequestBody(required = false) AiGradingRequest request,
            @RequestParam(name = "async", defaultValue = "false") boolean async,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        AiGradingRequest req = request != null ? request : new AiGradingRequest();

        if (async) {
            return enqueueGradingJob(submissionId, req, teacherId);
        }

        AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, req, teacherId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "AI chấm sơ bộ bài làm bất đồng bộ qua Redis Queue",
            description = "Đưa yêu cầu chấm bài vào hàng đợi Redis và nhận ngay 202 Accepted kèm jobId trong < 100ms")
    @PostMapping("/{submissionId}/ai-grading/async")
    @PreAuthorize("hasAuthority('submission:grade')")
    public ResponseEntity<AiJobSubmitResponse> requestAiGradingAsync(
            @PathVariable long submissionId,
            @RequestBody(required = false) AiGradingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        AiGradingRequest req = request != null ? request : new AiGradingRequest();
        return enqueueGradingJob(submissionId, req, teacherId);
    }

    private ResponseEntity<AiJobSubmitResponse> enqueueGradingJob(long submissionId, AiGradingRequest req, long teacherId) {
        AiGradingJobPayload payload = AiGradingJobPayload.builder()
                .submissionId(submissionId)
                .request(req)
                .teacherId(teacherId)
                .build();

        AiJobSubmitResponse submitResponse = aiJobService.submitJob("SUBMISSION_GRADING", teacherId, payload);
        return ResponseEntity.accepted().body(submitResponse);
    }
}
