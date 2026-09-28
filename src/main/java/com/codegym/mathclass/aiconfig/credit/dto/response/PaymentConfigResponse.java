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
public class PaymentConfigResponse {
    private Long id;
    private String bankCode;
    private String accountNumber;
    private String accountHolderName;
    private String sepayApiKey;
    private Boolean hasSepayApiKey;
    private String transferSyntaxPrefix;
    private String qrTemplate;
    private Boolean isActive;

    public static String maskApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String clean = rawKey.trim();
        if (clean.length() <= 8) {
            return clean.substring(0, Math.min(2, clean.length())) + "******";
        }
        return clean.substring(0, 4) + "****************" + clean.substring(clean.length() - 4);
    }

    public static PaymentConfigResponse fromEntity(PaymentConfig config) {
        boolean hasKey = config.getSepayApiKey() != null && !config.getSepayApiKey().isBlank();
        return PaymentConfigResponse.builder()
                .id(config.getId())
                .bankCode(config.getBankCode())
                .accountNumber(config.getAccountNumber())
                .accountHolderName(config.getAccountHolderName())
                .sepayApiKey(maskApiKey(config.getSepayApiKey()))
                .hasSepayApiKey(hasKey)
                .transferSyntaxPrefix(config.getTransferSyntaxPrefix())
                .qrTemplate(config.getQrTemplate())
                .isActive(config.getIsActive())
                .build();
    }
}
