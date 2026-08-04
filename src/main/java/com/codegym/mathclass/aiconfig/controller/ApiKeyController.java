package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.ApiKeyCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyStatusPatchRequest;
import com.codegym.mathclass.aiconfig.dto.response.ApiKeyResponse;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;
import com.codegym.mathclass.aiconfig.service.ApiKeyService;
import com.codegym.mathclass.aiconfig.service.ConnectionTestService;
import com.codegym.mathclass.common.annotation.ApiVersion;
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

@Tag(name = "Admin - AI API Keys", description = "APIs quản trị viên: Quản lý API Key cho từng Nhà cung cấp AI")
@RestController
@ApiVersion(1)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ConnectionTestService connectionTestService;

    @Operation(summary = "Lấy danh sách Key của Provider", description = "Truy vấn danh sách API Keys thuộc về một Provider (không trả về Plaintext key hay EncryptedKey)")
    @GetMapping("/providers/{providerId}/keys")
    public ResponseEntity<Map<String, List<ApiKeyResponse>>> getKeysByProviderId(@PathVariable Long providerId) {
        return ResponseEntity.ok(Map.of("data", apiKeyService.getKeysByProviderId(providerId)));
    }

    @Operation(summary = "Thêm API Key cho Provider", description = "Nhập API Key mới dạng plaintext, tự động mã hóa lưu CSDL")
    @PostMapping("/providers/{providerId}/keys")
    public ResponseEntity<ApiKeyResponse> addKey(@PathVariable Long providerId, @Valid @RequestBody ApiKeyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.addKey(providerId, request));
    }

    @Operation(summary = "Xóa API Key", description = "Xóa vĩnh viễn API Key khỏi hệ thống")
    @DeleteMapping("/keys/{keyId}")
    public ResponseEntity<Void> deleteKey(@PathVariable Long keyId) {
        apiKeyService.deleteKey(keyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cập nhật trạng thái Key", description = "Bật/Tắt trạng thái ACTIVE / INACTIVE cho API Key")
    @PatchMapping("/keys/{keyId}")
    public ResponseEntity<ApiKeyResponse> updateKeyStatus(@PathVariable Long keyId, @Valid @RequestBody ApiKeyStatusPatchRequest request) {
        return ResponseEntity.ok(apiKeyService.updateKeyStatus(keyId, request));
    }

    @Operation(summary = "Kiểm tra trạng thái Key (/verify)", description = "Giải mã Key trong CSDL và gửi request thử nghiệm tới Provider để xác thực còn hiệu lực và quota")
    @PostMapping("/keys/{keyId}/verify")
    public ResponseEntity<TestConnectionResponse> verifyKey(@PathVariable Long keyId) {
        return ResponseEntity.ok(connectionTestService.verifyKey(keyId));
    }
}
