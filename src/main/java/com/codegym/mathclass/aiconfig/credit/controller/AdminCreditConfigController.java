package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.DefaultCreditUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.TaskCreditConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.AiCreditConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.DefaultCreditResponse;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.user.entity.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - AI Credit Config", description = "APIs quản trị viên: chi phí credit theo task và credit mặc định theo role")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/ai-credit-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreditConfigController {

    private final AiCreditService aiCreditService;

    @Operation(summary = "Danh sách chi phí credit theo task")
    @GetMapping("/tasks")
    public ResponseEntity<List<AiCreditConfigResponse>> listTaskConfigs() {
        return ResponseEntity.ok(aiCreditService.getAllCreditConfigs());
    }

    @Operation(summary = "Cập nhật chi phí credit cho một task")
    @PutMapping("/tasks/{task}")
    public ResponseEntity<AiCreditConfigResponse> updateTaskConfig(
            @PathVariable String task,
            @Valid @RequestBody TaskCreditConfigUpdateRequest request) {
        boolean enabled = request.getEnabled() != null ? request.getEnabled() : true;
        return ResponseEntity.ok(aiCreditService.updateCreditConfig(
                task, request.getCostPerCall(), request.getTokensPerCredit(), enabled));
    }

    @Operation(summary = "Danh sách credit mặc định theo role")
    @GetMapping("/defaults")
    public ResponseEntity<List<DefaultCreditResponse>> listDefaults() {
        return ResponseEntity.ok(aiCreditService.getAllDefaults());
    }

    @Operation(summary = "Cập nhật credit mặc định theo role")
    @PutMapping("/defaults/{role}")
    public ResponseEntity<DefaultCreditResponse> updateDefault(
            @PathVariable Role role,
            @Valid @RequestBody DefaultCreditUpdateRequest request) {
        return ResponseEntity.ok(aiCreditService.updateDefaultCredits(role, request.getDefaultCredits()));
    }
}
