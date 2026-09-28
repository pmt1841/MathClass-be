package com.codegym.mathclass.aiconfig.credit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayWebhookRequest {

    private Long id;
    private String gateway;
    private String transactionDate;
    private String accountNumber;
    private String code;
    private String content;
    private String transferType;
    private Integer transferAmount;
    private Integer accumulated;
    private String subAccount;
    private String referenceCode;
    private String description;
}
