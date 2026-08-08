package com.codegym.mathclass.aiconfig.credit.gateway;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cổng thanh toán giả lập dành cho MVP: mọi đơn mua đều được xác nhận thành công.
 * Khi tích hợp cổng thanh toán thật, tạo implementation mới và đăng ký qua
 * {@link PaymentGatewayFactory} mà không sửa nghiệp vụ.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String getCode() {
        return "MOCK";
    }

    @Override
    public PaymentInitResult initiate(CreditPurchaseOrder order) {
        log.info("[MockPaymentGateway] Initiate order {} for user {}", order.getId(), order.getUserId());
        return new PaymentInitResult("PENDING", null, "Tạo phiên thanh toán giả lập thành công");
    }

    @Override
    public PaymentVerifyResult verify(CreditPurchaseOrder order) {
        log.info("[MockPaymentGateway] Verify order {} -> SUCCESS", order.getId());
        return new PaymentVerifyResult(true, "MOCK-" + order.getId(), "Thanh toán giả lập thành công");
    }
}
