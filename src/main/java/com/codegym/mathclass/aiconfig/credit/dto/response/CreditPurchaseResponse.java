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
    private String orderCode;
    private String gatewayCode;
    private String status;
    private String redirectUrl;
    private String qrUrl;
    private String transferSyntax;
    private String bankCode;
    private String accountNumber;
    private String accountHolderName;
    private Integer credits;
    private Integer price;
    private Integer creditsAdded;
    private Integer newBalance;

    public static CreditPurchaseResponse fromOrder(CreditPurchaseOrder order, PaymentInitResult init) {
        return CreditPurchaseResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .gatewayCode(order.getGatewayCode())
                .status(order.getStatus().name())
                .redirectUrl(init != null ? init.redirectUrl() : null)
                .qrUrl(init != null ? init.qrUrl() : null)
                .transferSyntax(init != null ? init.transferSyntax() : null)
                .bankCode(init != null ? init.bankCode() : null)
                .accountNumber(init != null ? init.accountNumber() : null)
                .accountHolderName(init != null ? init.accountHolderName() : null)
                .credits(order.getCredits())
                .price(order.getPrice())
                .build();
    }
}
