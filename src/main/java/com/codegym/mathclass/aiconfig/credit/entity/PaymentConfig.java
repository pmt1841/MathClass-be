package com.codegym.mathclass.aiconfig.credit.entity;

import com.codegym.mathclass.aiconfig.security.ApiKeyCryptoConverter;
import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfig extends BaseEntity {

    @Column(name = "bank_code", nullable = false, length = 20)
    @Builder.Default
    private String bankCode = "MB";

    @Column(name = "account_number", nullable = false, length = 50)
    @Builder.Default
    private String accountNumber = "";

    @Column(name = "account_holder_name", nullable = false, length = 100)
    @Builder.Default
    private String accountHolderName = "";

    @Convert(converter = ApiKeyCryptoConverter.class)
    @Column(name = "sepay_api_key", columnDefinition = "TEXT")
    private String sepayApiKey;

    @Column(name = "transfer_syntax_prefix", nullable = false, length = 20)
    @Builder.Default
    private String transferSyntaxPrefix = "MAT";

    @Column(name = "qr_template", nullable = false, length = 20)
    @Builder.Default
    private String qrTemplate = "compact2";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
