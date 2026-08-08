package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentInitResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditPurchaseResponse {
    private Long orderId;
    private String gatewayCode;
    private String status;
    private String redirectUrl;
    private Integer credits;
    private Integer price;
    private Integer creditsAdded;
    private Integer newBalance;

    public static CreditPurchaseResponse fromOrder(CreditPurchaseOrder order, PaymentInitResult init) {
        return CreditPurchaseResponse.builder()
                .orderId(order.getId())
                .gatewayCode(order.getGatewayCode())
                .status(order.getStatus().name())
                .redirectUrl(init != null ? init.redirectUrl() : null)
                .credits(order.getCredits())
                .price(order.getPrice())
                .build();
    }
}
