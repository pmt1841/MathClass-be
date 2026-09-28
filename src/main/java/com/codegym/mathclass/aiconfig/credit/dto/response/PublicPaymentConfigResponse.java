package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicPaymentConfigResponse {
    private String bankCode;
    private String accountNumber;
    private String accountHolderName;
    private String transferSyntaxPrefix;
    private String qrTemplate;
    private Boolean isActive;

    public static PublicPaymentConfigResponse fromEntity(PaymentConfig config) {
        return PublicPaymentConfigResponse.builder()
                .bankCode(config.getBankCode())
                .accountNumber(config.getAccountNumber())
                .accountHolderName(config.getAccountHolderName())
                .transferSyntaxPrefix(config.getTransferSyntaxPrefix())
                .qrTemplate(config.getQrTemplate())
                .isActive(config.getIsActive())
                .build();
    }
}
