package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageCreateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin - Credit Packages", description = "APIs quản trị viên: quản lý gói credit")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/credit-packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreditPackageController {

    private final AiCreditService aiCreditService;

    @Operation(summary = "Danh sách tất cả gói credit")
    @GetMapping
    public ResponseEntity<List<CreditPackageResponse>> listPackages() {
        return ResponseEntity.ok(aiCreditService.getAllPackages());
    }

    @Operation(summary = "Tạo gói credit mới")
    @PostMapping
    public ResponseEntity<CreditPackageResponse> create(@Valid @RequestBody CreditPackageCreateRequest request) {
        return ResponseEntity.ok(aiCreditService.createPackage(request));
    }

    @Operation(summary = "Cập nhật gói credit")
    @PutMapping("/{id}")
    public ResponseEntity<CreditPackageResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreditPackageUpdateRequest request) {
        return ResponseEntity.ok(aiCreditService.updatePackage(id, request));
    }

    @Operation(summary = "Xóa gói credit")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        aiCreditService.deletePackage(id);
        return ResponseEntity.ok(Map.of("message", "Xóa gói credit thành công"));
    }
}
