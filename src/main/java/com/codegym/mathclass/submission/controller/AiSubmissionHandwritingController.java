package com.codegym.mathclass.submission.controller;

import com.codegym.mathclass.aiqueue.dto.AiJobSubmitResponse;
import com.codegym.mathclass.aiqueue.dto.payload.AiHandwritingJobPayload;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import com.codegym.mathclass.submission.service.AiSubmissionHandwritingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Submission AI Features", description = "APIs hỗ trợ học sinh nhận diện chữ viết tay và nắn chỉnh nét vẽ phác thảo")
@RestController
@ApiVersion(1)
@RequestMapping("/submissions/ai")
@RequiredArgsConstructor
public class AiSubmissionHandwritingController {

    private final AiSubmissionHandwritingService aiSubmissionHandwritingService;
    private final AiJobService aiJobService;

    @Operation(summary = "Nhận diện chữ viết tay sang mã LaTeX",
            description = "Gửi dữ liệu ảnh chữ viết tay/công thức toán dạng Base64 để AI phân tích và trả về mã LaTeX chuẩn. Hỗ trợ async=true để đưa vào hàng đợi Redis")
    @PostMapping("/handwriting-to-latex")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> convertHandwritingToLatex(
            @Valid @RequestBody HandwritingLatexRequest request,
            @RequestParam(name = "async", defaultValue = "false") boolean async,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        if (async) {
            return enqueueHandwritingJob("LATEX", request, null, userId);
        }

        HandwritingLatexResponse response = aiSubmissionHandwritingService.convertHandwritingToLatex(request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Nhận diện chữ viết tay bất đồng bộ qua Redis Queue",
            description = "Đưa yêu cầu OCR chữ viết tay vào hàng đợi Redis, phản hồi 202 Accepted kèm jobId trong < 100ms")
    @PostMapping("/handwriting-to-latex/async")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiJobSubmitResponse> convertHandwritingToLatexAsync(
            @Valid @RequestBody HandwritingLatexRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        return enqueueHandwritingJob("LATEX", request, null, userId);
    }

    @Operation(summary = "Nắn chỉnh phác thảo nét vẽ hình học sang Canvas",
            description = "Gửi hình phác thảo tự do dạng Base64 để AI nắn chỉnh nét vẽ thành cấu trúc đối tượng hình học phẳng chuẩn hóa. Hỗ trợ async=true để đưa vào hàng đợi Redis")
    @PostMapping("/sketch-to-geometry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> normalizeSketchToGeometry(
            @Valid @RequestBody SketchGeometryRequest request,
            @RequestParam(name = "async", defaultValue = "false") boolean async,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        if (async) {
            return enqueueHandwritingJob("SKETCH_GEOMETRY", null, request, userId);
        }

        SketchGeometryResponse response = aiSubmissionHandwritingService.normalizeSketchToGeometry(request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Nắn chỉnh phác thảo nét vẽ hình học bất đồng bộ qua Redis Queue",
            description = "Đưa yêu cầu nắn chỉnh hình vẽ vào hàng đợi Redis, phản hồi 202 Accepted kèm jobId trong < 100ms")
    @PostMapping("/sketch-to-geometry/async")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiJobSubmitResponse> normalizeSketchToGeometryAsync(
            @Valid @RequestBody SketchGeometryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        return enqueueHandwritingJob("SKETCH_GEOMETRY", null, request, userId);
    }

    private ResponseEntity<AiJobSubmitResponse> enqueueHandwritingJob(
            String subTask,
            HandwritingLatexRequest latexReq,
            SketchGeometryRequest sketchReq,
            Long userId) {

        AiHandwritingJobPayload payload = AiHandwritingJobPayload.builder()
                .subTask(subTask)
                .latexRequest(latexReq)
                .sketchRequest(sketchReq)
                .userId(userId)
                .build();

        AiJobSubmitResponse submitResponse = aiJobService.submitJob("CANVAS_LATEX", userId, payload);
        return ResponseEntity.accepted().body(submitResponse);
    }
}
