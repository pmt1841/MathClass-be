package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.ProviderCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ProviderUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.request.TestConnectionRequest;
import com.codegym.mathclass.aiconfig.dto.response.ProviderResponse;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;
import com.codegym.mathclass.aiconfig.service.ConnectionTestService;
import com.codegym.mathclass.aiconfig.service.ProviderService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.systemlog.annotation.AuditLog;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin - AI Providers", description = "APIs quản trị viên: Quản lý Nhà cung cấp dịch vụ AI (Providers)")
@RestController
@ApiVersion(1)
@RequestMapping("/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProviderController {

    private final ProviderService providerService;
    private final ConnectionTestService connectionTestService;

    @Operation(summary = "Lấy danh sách Provider", description = "Truy vấn danh sách tất cả các AI Providers")
    @GetMapping
    public ResponseEntity<Map<String, List<ProviderResponse>>> getAllProviders() {
        return ResponseEntity.ok(Map.of("data", providerService.getAllProviders()));
    }

    @Operation(summary = "Lấy danh sách Model khả dụng của Provider", description = "Gọi REST API tới Provider để lấy danh sách tên các AI Model khả dụng")
    @GetMapping("/{id}/models")
    public ResponseEntity<Map<String, List<String>>> getProviderModels(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("data", connectionTestService.fetchAvailableModels(id)));
    }

    @Operation(summary = "Tạo mới Provider", description = "Tạo mới một Provider (code duy nhất, viết hoa)")
    @PostMapping
    @AuditLog(action = "CREATE_AI_PROVIDER", resourceType = "AI_CONFIG")
    public ResponseEntity<ProviderResponse> createProvider(@Valid @RequestBody ProviderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.createProvider(request));
    }

    @Operation(summary = "Cập nhật Provider", description = "Cập nhật thông tin Provider theo ID (không cho phép sửa code)")
    @PutMapping("/{id}")
    @AuditLog(action = "UPDATE_AI_PROVIDER", resourceType = "AI_CONFIG")
    public ResponseEntity<ProviderResponse> updateProvider(@PathVariable Long id, @Valid @RequestBody ProviderUpdateRequest request) {
        return ResponseEntity.ok(providerService.updateProvider(id, request));
    }

    @Operation(summary = "Xóa Provider", description = "Xóa vĩnh viễn Provider nếu không được Task nào sử dụng")
    @DeleteMapping("/{id}")
    @AuditLog(action = "DELETE_AI_PROVIDER", resourceType = "AI_CONFIG", level = SystemLogLevel.WARNING)
    public ResponseEntity<Void> deleteProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Thử kết nối tới Provider (Test Connection)", description = "Kiểm tra tính hợp lệ của API Key, Model và độ trễ phản hồi")
    @PostMapping("/test")
    public ResponseEntity<TestConnectionResponse> testConnection(@Valid @RequestBody TestConnectionRequest request) {
        return ResponseEntity.ok(connectionTestService.testConnection(request));
    }
}
