package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.SystemPromptCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptResetRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptHistoryResponse;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptResponse;
import com.codegym.mathclass.aiconfig.service.SystemPromptService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin - System Prompts", description = "APIs quản trị viên: Quản lý câu lệnh mẫu System Prompts điều khiển AI")
@RestController
@ApiVersion(1)
@RequestMapping("/system-prompts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemPromptController {

    private final SystemPromptService systemPromptService;

    @Operation(summary = "Lấy danh sách System Prompt", description = "Truy vấn danh sách System Prompts có hỗ trợ lọc theo taskCode, status và tìm kiếm")
    @GetMapping
    public ResponseEntity<Map<String, List<SystemPromptResponse>>> getAllPrompts(
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(Map.of("data", systemPromptService.getAllPrompts(taskCode, status, search)));
    }

    @Operation(summary = "Xem chi tiết System Prompt", description = "Lấy chi tiết cấu hình System Prompt bao gồm defaultContent và currentContent")
    @GetMapping("/{id}")
    public ResponseEntity<SystemPromptResponse> getPromptById(@PathVariable Long id) {
        return ResponseEntity.ok(systemPromptService.getPromptById(id));
    }

    @Operation(summary = "Tạo mới System Prompt", description = "Tạo câu lệnh System Prompt mới với mã code duy nhất và danh sách biến cho phép")
    @PostMapping
    public ResponseEntity<SystemPromptResponse> createPrompt(
            @Valid @RequestBody SystemPromptCreateRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        String adminEmail = getAdminEmail(authentication);
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(systemPromptService.createPrompt(request, adminEmail, ipAddress));
    }

    @Operation(summary = "Cập nhật System Prompt", description = "Cập nhật tên, nội dung currentContent, mô tả và trạng thái của Prompt (Strict Validation biến môi trường)")
    @PutMapping("/{id}")
    public ResponseEntity<SystemPromptResponse> updatePrompt(
            @PathVariable Long id,
            @Valid @RequestBody SystemPromptUpdateRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        String adminEmail = getAdminEmail(authentication);
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(systemPromptService.updatePrompt(id, request, adminEmail, ipAddress));
    }

    @Operation(summary = "Khôi phục mặc định (Reset to Default)", description = "Khôi phục currentContent về giá trị mặc định gốc defaultContent")
    @PostMapping("/{id}/reset")
    public ResponseEntity<SystemPromptResponse> resetToDefault(
            @PathVariable Long id,
            @RequestBody(required = false) SystemPromptResetRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        String adminEmail = getAdminEmail(authentication);
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(systemPromptService.resetToDefault(id, request, adminEmail, ipAddress));
    }

    @Operation(summary = "Lấy lịch sử phiên bản Prompt", description = "Xem danh sách tất cả các phiên bản đã lưu trong quá khứ của Prompt")
    @GetMapping("/{id}/history")
    public ResponseEntity<Map<String, List<SystemPromptHistoryResponse>>> getPromptHistory(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("data", systemPromptService.getPromptHistory(id)));
    }

    @Operation(summary = "Rollback về phiên bản cũ", description = "Khôi phục currentContent của Prompt về nội dung phiên bản lịch sử chỉ định")
    @PostMapping("/{id}/rollback/{historyId}")
    public ResponseEntity<SystemPromptResponse> rollbackToVersion(
            @PathVariable Long id,
            @PathVariable Long historyId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        String adminEmail = getAdminEmail(authentication);
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(systemPromptService.rollbackToVersion(id, historyId, adminEmail, ipAddress));
    }

    @Operation(summary = "Xóa System Prompt", description = "Xóa vĩnh viễn System Prompt khỏi CSDL")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrompt(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        String adminEmail = getAdminEmail(authentication);
        String ipAddress = servletRequest.getRemoteAddr();
        systemPromptService.deletePrompt(id, adminEmail, ipAddress);
        return ResponseEntity.noContent().build();
    }

    private String getAdminEmail(Authentication authentication) {
        return (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "admin@mathclass.edu.vn";
    }
}
