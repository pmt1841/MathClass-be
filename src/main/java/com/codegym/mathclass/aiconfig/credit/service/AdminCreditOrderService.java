package com.codegym.mathclass.aiconfig.credit.service;

import com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderAdminResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminCreditOrderService {

    Page<CreditOrderAdminResponse> listOrders(CreditPurchaseOrderStatus status, String search, Pageable pageable);

    CreditOrderAdminResponse manualApproveOrder(Long orderId);

    CreditOrderAdminResponse refundOrder(Long orderId, String refundReason);
}
