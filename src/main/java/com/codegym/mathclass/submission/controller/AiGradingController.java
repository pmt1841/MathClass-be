package com.codegym.mathclass.submission.controller;

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
import org.springframework.web.bind.annotation.*;

/**
 * MAT-250: API AI chấm sơ bộ bài làm của học sinh cho giáo viên.
 *
 * Endpoint chỉ dành cho giáo viên (permission submission:grade — cùng quyền chấm điểm thủ công).
 * Kết quả trả về là DỰ THẢO (điểm + nhận xét + lỗi hình vẽ), KHÔNG tự ghi vào submission.
 */
@Tag(name = "AI Grading", description = "APIs AI chấm sơ bộ bài làm (đối chiếu hình vẽ Canvas + chấm tự luận) cho giáo viên")
@RestController
@ApiVersion(1)
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class AiGradingController {

    private final AiGradingService aiGradingService;

    @Operation(summary = "AI chấm sơ bộ bài làm",
            description = "Giáo viên bấm AI chấm sơ bộ: AI đối chiếu hình vẽ Canvas của học sinh với hình mẫu, "
                    + "chấm điểm dự kiến (thang maxScore) và viết dự thảo nhận xét. Kết quả chỉ là dự thảo, "
                    + "không ghi đè điểm/feedback đã lưu.")
    @PostMapping("/{submissionId}/ai-grading")
    @PreAuthorize("hasAuthority('submission:grade')")
    public ResponseEntity<AiGradingResponse> requestAiGrading(
            @PathVariable long submissionId,
            @RequestBody(required = false) AiGradingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long teacherId = userDetails.getId();
        AiGradingRequest req = request != null ? request : new AiGradingRequest();
        AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, req, teacherId);
        return ResponseEntity.ok(response);
    }
}
