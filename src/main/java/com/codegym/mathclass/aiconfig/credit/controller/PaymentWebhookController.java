package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.SepayWebhookRequest;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.aiconfig.credit.service.PaymentWebhookService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@ApiVersion(1)
@RequestMapping("/payment/webhook")
@RequiredArgsConstructor
@Tag(name = "Payment Webhook", description = "Endpoint nhận biến động số dư từ SePay")
public class PaymentWebhookController {

    private final PaymentConfigService paymentConfigService;
    private final PaymentWebhookService paymentWebhookService;

    @Operation(summary = "Nhận Webhook từ SePay khi có biến động số dư ngân hàng (Nạp tiền & Hoàn tiền)")
    @PostMapping("/sepay")
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody SepayWebhookRequest request) {

        log.info("[SepayWebhookController] Nhận webhook: id={}, type={}, amount={}",
                request.getId(), request.getTransferType(), request.getTransferAmount());

        if (!paymentConfigService.verifySepayApiKey(authorizationHeader)) {
            log.warn("[SepayWebhookController] Xác thực API Key thất bại");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized API key"));
        }

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);
        return ResponseEntity.ok(result);
    }
}
