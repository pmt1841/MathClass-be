package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.service.AiQuestionService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
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

@Tag(name = "AI Math Question Generator", description = "APIs hỗ trợ Giáo viên tự động sinh bài toán và hình vẽ Canvas 2D sử dụng AI (Gemini 2.0)")
@RestController
@ApiVersion(1)
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiQuestionController {

    private final AiQuestionService aiQuestionService;

    @Operation(summary = "Sinh câu hỏi Toán học tự động bằng AI", description = "Nhận Prompt + Bộ lọc (Khối lớp, Mức độ, Chủ đề), gọi Gemini 2.0 để sinh bài toán chuẩn KaTeX và Canvas 2D Data")
    @PostMapping("/generate-question")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') or hasAuthority('assignment:create')")
    public ResponseEntity<AiGeneratedQuestionResponse> generateQuestion(
            @Valid @RequestBody GenerateQuestionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        AiGeneratedQuestionResponse result = aiQuestionService.generateQuestion(request, userId);
        return ResponseEntity.ok(result);
    }
}
