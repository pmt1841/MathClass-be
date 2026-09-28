package com.codegym.mathclass.aiconfig.credit.gateway;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SepayPaymentGateway implements PaymentGateway {

    public static final String GATEWAY_CODE = "SEPAY_VIETQR";

    private final PaymentConfigService paymentConfigService;

    @Override
    public String getCode() {
        return GATEWAY_CODE;
    }

    @Override
    public PaymentInitResult initiate(CreditPurchaseOrder order) {
        PaymentConfig config = paymentConfigService.getPaymentConfig();

        String prefix = (config != null && StringUtils.hasText(config.getTransferSyntaxPrefix()))
                ? config.getTransferSyntaxPrefix().trim().toUpperCase()
                : "MAT";

        String code = order.getOrderCode() != null ? order.getOrderCode() : String.valueOf(order.getId());
        String syntax = prefix + " " + code;
        String accountHolderName = (config != null && config.getAccountHolderName() != null)
                ? config.getAccountHolderName()
                : "";
        String encodedName = URLEncoder.encode(accountHolderName, StandardCharsets.UTF_8);
        String encodedSyntax = URLEncoder.encode(syntax, StandardCharsets.UTF_8);

        String bankCode = config != null ? config.getBankCode() : "";
        String accountNumber = config != null ? config.getAccountNumber() : "";
        String qrTemplate = config != null ? config.getQrTemplate() : "compact2";

        String qrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%d&addInfo=%s&accountName=%s",
                bankCode,
                accountNumber,
                qrTemplate,
                order.getPrice(),
                encodedSyntax,
                encodedName
        );

        log.info("[SepayPaymentGateway] Tạo VietQR cho đơn {}: bank={}, account={}, amount={}, syntax={}",
                order.getId(), bankCode, accountNumber, order.getPrice(), syntax);

        return new PaymentInitResult(
                CreditPurchaseOrderStatus.PENDING.name(),
                null,
                qrUrl,
                syntax,
                bankCode,
                accountNumber,
                accountHolderName,
                "Khởi tạo đơn hàng VietQR thành công"
        );
    }

    @Override
    public PaymentVerifyResult verify(CreditPurchaseOrder order) {
        if (order.getStatus() == CreditPurchaseOrderStatus.SUCCESS) {
            return new PaymentVerifyResult(true, order.getTransactionRef(), "Thanh toán thành công");
        }
        return new PaymentVerifyResult(false, null, "Đang chờ thanh toán chuyển khoản qua VietQR");
    }
}
