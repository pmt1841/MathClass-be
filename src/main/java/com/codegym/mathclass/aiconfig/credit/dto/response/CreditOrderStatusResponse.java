package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditOrderStatusResponse {
    private Long orderId;
    private CreditPurchaseOrderStatus status;
    private Integer credits;
    private Integer price;
    private Integer creditsAdded;
    private Integer newBalance;
    private String transactionRef;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static CreditOrderStatusResponse fromOrder(CreditPurchaseOrder order, Integer newBalance) {
        return CreditOrderStatusResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .credits(order.getCredits())
                .price(order.getPrice())
                .creditsAdded(order.getStatus() == CreditPurchaseOrderStatus.SUCCESS ? order.getCredits() : null)
                .newBalance(newBalance)
                .transactionRef(order.getTransactionRef())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
