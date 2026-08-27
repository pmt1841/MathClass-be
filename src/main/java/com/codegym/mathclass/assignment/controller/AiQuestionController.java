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

import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.service.AiBatchQuestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;

@Tag(name = "AI Math Question Generator", description = "APIs hỗ trợ Giáo viên tự động sinh bài toán và hình vẽ Canvas 2D sử dụng AI (Gemini 2.0)")
@RestController
@ApiVersion(1)
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiQuestionController {

    private final AiQuestionService aiQuestionService;
    private final AiBatchQuestionService aiBatchQuestionService;

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

    @Operation(summary = "Tạo hàng loạt bài tập từ tài liệu/Word bằng AI", description = "Tải file Word/PDF/TXT hoặc gửi text đề thi, AI bóc tách thành danh sách bài tập chuẩn KaTeX")
    @PostMapping(value = "/batch-generate-questions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') or hasAuthority('assignment:create')")
    public ResponseEntity<BatchGenerateQuestionsResponse> batchGenerateQuestions(
            @ModelAttribute BatchGenerateQuestionsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        BatchGenerateQuestionsResponse result = aiBatchQuestionService.batchGenerateQuestions(request, userId);
        return ResponseEntity.ok(result);
    }
}
