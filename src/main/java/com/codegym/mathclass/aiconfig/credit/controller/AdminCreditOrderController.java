package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.RefundCreditOrderRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderAdminResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.service.AdminCreditOrderService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Credit Orders", description = "APIs quản trị viên: theo dõi danh sách đơn nạp credit, hoàn tiền và duyệt thủ công")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/credit-orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreditOrderController {

    private final AdminCreditOrderService adminCreditOrderService;

    @Operation(summary = "Lấy danh sách đơn nạp credit (có phân trang, tìm kiếm mã đơn và lọc theo trạng thái)")
    @GetMapping
    public ResponseEntity<Page<CreditOrderAdminResponse>> listOrders(
            @RequestParam(required = false) CreditPurchaseOrderStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminCreditOrderService.listOrders(status, search, pageable));
    }

    @Operation(summary = "Duyệt nạp credit thủ công cho đơn ở trạng thái PENDING")
    @PostMapping("/{orderId}/approve")
    public ResponseEntity<CreditOrderAdminResponse> approveOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(adminCreditOrderService.manualApproveOrder(orderId));
    }

    @Operation(summary = "Hoàn tiền cho đơn nạp credit bị lỗi (quá hạn hoặc chuyển khoản trùng mã)")
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<CreditOrderAdminResponse> refundOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) RefundCreditOrderRequest request) {
        String reason = request != null ? request.getRefundReason() : null;
        return ResponseEntity.ok(adminCreditOrderService.refundOrder(orderId, reason));
    }
}
