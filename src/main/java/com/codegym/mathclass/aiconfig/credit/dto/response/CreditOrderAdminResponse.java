package com.codegym.mathclass.aiconfig.credit.dto.response;

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
public class CreditOrderAdminResponse {
    private Long orderId;
    private String orderCode;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private Long packageId;
    private Integer credits;
    private Integer price;
    private String gatewayCode;
    private CreditPurchaseOrderStatus status;
    private String transactionRef;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private String refundReason;
    private LocalDateTime refundedAt;
    private String refundRecipientInfo;
    private String refundBankCode;
    private String refundAccountNumber;
    private String refundAccountName;
    private Long bugReportId;
}
