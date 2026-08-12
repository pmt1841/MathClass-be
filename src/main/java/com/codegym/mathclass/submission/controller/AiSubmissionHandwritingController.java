package com.codegym.mathclass.submission.controller;

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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Submission AI Features", description = "APIs hỗ trợ học sinh nhận diện chữ viết tay và nắn chỉnh nét vẽ phác thảo")
@RestController
@ApiVersion(1)
@RequestMapping("/submissions/ai")
@RequiredArgsConstructor
public class AiSubmissionHandwritingController {

    private final AiSubmissionHandwritingService aiSubmissionHandwritingService;

    @Operation(summary = "Nhận diện chữ viết tay sang mã LaTeX",
            description = "Gửi dữ liệu ảnh chữ viết tay/công thức toán dạng Base64 để AI phân tích và trả về mã LaTeX chuẩn.")
    @PostMapping("/handwriting-to-latex")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HandwritingLatexResponse> convertHandwritingToLatex(
            @Valid @RequestBody HandwritingLatexRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        HandwritingLatexResponse response = aiSubmissionHandwritingService.convertHandwritingToLatex(request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Nắn chỉnh phác thảo nét vẽ hình học sang Canvas",
            description = "Gửi hình phác thảo tự do dạng Base64 để AI nắn chỉnh nét vẽ thành cấu trúc đối tượng hình học phẳng chuẩn hóa.")
    @PostMapping("/sketch-to-geometry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SketchGeometryResponse> normalizeSketchToGeometry(
            @Valid @RequestBody SketchGeometryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        SketchGeometryResponse response = aiSubmissionHandwritingService.normalizeSketchToGeometry(request, userId);
        return ResponseEntity.ok(response);
    }
}

