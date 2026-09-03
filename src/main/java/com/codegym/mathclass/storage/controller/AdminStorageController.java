package com.codegym.mathclass.storage.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.storage.dto.StorageCleanupRequest;
import com.codegym.mathclass.storage.dto.StorageCleanupResponse;
import com.codegym.mathclass.storage.dto.StorageCleanupStatusResponse;
import com.codegym.mathclass.storage.service.StorageCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Cloud Storage", description = "APIs quản lý và dọn dẹp dung lượng lưu trữ Cloud Storage (Supabase)")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/storage/cleanup")
@RequiredArgsConstructor
public class AdminStorageController {

    private final StorageCleanupService storageCleanupService;

    @Operation(summary = "Kích hoạt dọn dẹp ảnh rác trên Cloud (Admin)", description = "Quét đối soát giữa DB và Supabase Storage, xóa các file mồ côi quá thời gian đệm")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StorageCleanupResponse> triggerCleanup(
            @Valid @RequestBody(required = false) StorageCleanupRequest request) {
        StorageCleanupResponse response = storageCleanupService.runCleanup(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy trạng thái cấu hình và lịch sử dọn dẹp (Admin)", description = "Xem cấu hình cron job, grace period và kết quả lần dọn dẹp gần nhất")
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StorageCleanupStatusResponse> getCleanupStatus() {
        StorageCleanupStatusResponse status = storageCleanupService.getCleanupStatus();
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Cập nhật cấu hình lịch dọn dẹp và thời gian đệm (Admin)", description = "Bật/tắt dọn dẹp tự động, đổi biểu thức cron và thời gian đệm an toàn")
    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StorageCleanupStatusResponse> updateConfig(
            @Valid @RequestBody com.codegym.mathclass.storage.dto.UpdateStorageCleanupConfigRequest request) {
        StorageCleanupStatusResponse updatedStatus = storageCleanupService.updateConfig(request);
        return ResponseEntity.ok(updatedStatus);
    }
}
