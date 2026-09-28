package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPurchaseRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditBalanceResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderStatusResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPurchaseResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.PublicPaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.CreditPurchaseService;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "AI Credits", description = "APIs tài khoản Credit AI của người dùng: số dư, gói credit, mua/nạp credit")
@RestController
@ApiVersion(1)
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditController {

    private final AiCreditService aiCreditService;
    private final CreditPurchaseService creditPurchaseService;
    private final PaymentConfigService paymentConfigService;

    @Operation(summary = "Số dư & bảng giá credit của tôi")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditBalanceResponse> getMyBalance(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiCreditService.getMyCreditInfo(userDetails.getId()));
    }

    @Operation(summary = "Lấy thông tin tài khoản ngân hàng nhận tiền công khai")
    @GetMapping("/payment-config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PublicPaymentConfigResponse> getPublicPaymentConfig() {
        return ResponseEntity.ok(paymentConfigService.getPublicConfig());
    }

    @Operation(summary = "Lịch sử giao dịch credit của tôi")
    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CreditTransactionResponse>> getMyTransactions(
            @RequestParam(required = false) CreditTransactionType type,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(aiCreditService.getTransactions(userDetails.getId(), type, pageable));
    }

    @Operation(summary = "Danh sách gói credit đang bán")
    @GetMapping("/packages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CreditPackageResponse>> listPackages() {
        return ResponseEntity.ok(aiCreditService.getEnabledPackages());
    }

    @Operation(summary = "Mua gói credit (tạo đơn + khởi tạo thanh toán VietQR)")
    @PostMapping("/purchase")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditPurchaseResponse> purchase(
            @Valid @RequestBody CreditPurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(creditPurchaseService.createPurchase(userDetails.getId(), request));
    }

    @Operation(summary = "Kiểm tra trạng thái đơn nạp credit (Polling realtime)")
    @GetMapping("/purchase/orders/{orderId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditOrderStatusResponse> getOrderStatus(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(creditPurchaseService.getOrderStatus(userDetails.getId(), orderId));
    }

    @Operation(summary = "Xác nhận thanh toán đơn mua credit")
    @PostMapping("/purchase/{orderId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditPurchaseResponse> completePurchase(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(creditPurchaseService.completePurchase(userDetails.getId(), orderId));
    }

    @Operation(summary = "Hoàn lại credit cho tác vụ AI khi người dùng bấm Hủy tiến trình")
    @PostMapping("/refund-task")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> refundTask(
            @RequestParam String task,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        aiCreditService.refundTaskIfReserved(userDetails.getId(), task);
        return ResponseEntity.ok(Map.of("message", "Đã hoàn lại credit cho tác vụ " + task));
    }
}
