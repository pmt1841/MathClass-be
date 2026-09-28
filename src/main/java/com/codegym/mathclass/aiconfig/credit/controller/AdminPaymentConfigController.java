package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.PaymentConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.PaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Payment Config", description = "APIs quản trị viên: cấu hình cổng thanh toán VietQR và Webhook SePay")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/payment-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentConfigController {

    private final PaymentConfigService paymentConfigService;

    @Operation(summary = "Lấy thông tin cấu hình thanh toán hiện tại (Admin)")
    @GetMapping
    public ResponseEntity<PaymentConfigResponse> getConfig() {
        return ResponseEntity.ok(paymentConfigService.getConfig());
    }

    @Operation(summary = "Cập nhật cấu hình cổng thanh toán ngân hàng & SePay API Key")
    @PutMapping
    public ResponseEntity<PaymentConfigResponse> updateConfig(
            @Valid @RequestBody PaymentConfigUpdateRequest request) {
        return ResponseEntity.ok(paymentConfigService.updateConfig(request));
    }

    @Operation(summary = "Bật/Tắt tức thì trạng thái cổng thanh toán VietQR")
    @PatchMapping("/toggle-active")
    public ResponseEntity<PaymentConfigResponse> toggleActive(@RequestParam boolean active) {
        return ResponseEntity.ok(paymentConfigService.toggleActive(active));
    }
}
