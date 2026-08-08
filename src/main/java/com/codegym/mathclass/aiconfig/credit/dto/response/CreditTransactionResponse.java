package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditTransactionResponse {
    private Long id;
    private Long userId;
    private Integer amount;
    private String type;
    private String task;
    private Long referenceId;
    private String description;
    private LocalDateTime createdAt;

    public static CreditTransactionResponse from(CreditTransaction txn) {
        return CreditTransactionResponse.builder()
                .id(txn.getId())
                .userId(txn.getUserId())
                .amount(txn.getAmount())
                .type(txn.getType().name())
                .task(txn.getTask())
                .referenceId(txn.getReferenceId())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
