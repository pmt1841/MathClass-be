package com.codegym.mathclass.aiqueue.controller;

import com.codegym.mathclass.aiqueue.dto.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Job Queue", description = "APIs quản lý và tra cứu trạng thái tác vụ AI chạy bất đồng bộ trong hàng đợi Redis")
@RestController
@ApiVersion(1)
@RequestMapping("/ai/jobs")
@RequiredArgsConstructor
public class AiJobController {

    private final AiJobService aiJobService;

    @Operation(summary = "Tra cứu trạng thái và kết quả tác vụ AI",
            description = "Cho phép người dùng kiểm tra tiến độ xử lý tác vụ AI (QUEUED, PROCESSING, RETRYING, COMPLETED, FAILED) khi mất kết nối SSE")
    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiJobResultResponse> getJobStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        AiJobResultResponse result = aiJobService.getJobStatus(jobId, userDetails.getId(), isAdmin);
        return ResponseEntity.ok(result);
    }
}
