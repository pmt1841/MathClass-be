package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal - System Prompts Render", description = "API dùng nội bộ: Render System Prompt đã thay thế biến môi trường phục vụ gọi AI Service")
@RestController
@ApiVersion(1)
@RequestMapping("/system-prompts")
@RequiredArgsConstructor
public class InternalPromptController {

    private final PromptRenderService promptRenderService;

    @Operation(summary = "Render System Prompt", description = "Truy vấn System Prompt theo promptCode và tự động thay thế các biến {{variable_name}} thành dữ liệu thực tế")
    @PostMapping("/render")
    public ResponseEntity<RenderPromptResponse> renderPrompt(@Valid @RequestBody RenderPromptRequest request) {
        return ResponseEntity.ok(promptRenderService.renderPrompt(request));
    }
}
