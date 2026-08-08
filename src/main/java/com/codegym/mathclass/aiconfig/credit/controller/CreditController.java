package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPurchaseRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditBalanceResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPurchaseResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.CreditPurchaseService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI Credits", description = "APIs tài khoản Credit AI của người dùng: số dư, gói credit, mua/nạp credit")
@RestController
@ApiVersion(1)
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditController {

    private final AiCreditService aiCreditService;
    private final CreditPurchaseService creditPurchaseService;

    @Operation(summary = "Số dư & bảng giá credit của tôi")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditBalanceResponse> getMyBalance(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiCreditService.getMyCreditInfo(userDetails.getId()));
    }

    @Operation(summary = "Lịch sử giao dịch credit của tôi")
    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CreditTransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiCreditService.getTransactions(userDetails.getId(), null));
    }

    @Operation(summary = "Danh sách gói credit đang bán")
    @GetMapping("/packages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CreditPackageResponse>> listPackages() {
        return ResponseEntity.ok(aiCreditService.getEnabledPackages());
    }

    @Operation(summary = "Mua gói credit (tạo đơn + khởi tạo thanh toán)")
    @PostMapping("/purchase")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditPurchaseResponse> purchase(
            @Valid @RequestBody CreditPurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(creditPurchaseService.createPurchase(userDetails.getId(), request));
    }

    @Operation(summary = "Xác nhận thanh toán đơn mua credit")
    @PostMapping("/purchase/{orderId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditPurchaseResponse> completePurchase(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(creditPurchaseService.completePurchase(userDetails.getId(), orderId));
    }
}
