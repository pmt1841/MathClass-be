package com.codegym.mathclass.aiconfig.credit.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditPurchaseOrder extends BaseEntity {

    @Column(name = "order_code", nullable = false, unique = true, length = 30)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "credits", nullable = false)
    private Integer credits;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "gateway_code", nullable = false, length = 20)
    @Builder.Default
    private String gatewayCode = "MOCK";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CreditPurchaseOrderStatus status = CreditPurchaseOrderStatus.PENDING;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_bank_code", length = 20)
    private String refundBankCode;

    @Column(name = "refund_account_number", length = 50)
    private String refundAccountNumber;

    @Column(name = "refund_account_name", length = 150)
    private String refundAccountName;
}
