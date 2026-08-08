package com.codegym.mathclass.aiconfig.credit.gateway;

import com.codegym.mathclass.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory lựa chọn {@link PaymentGateway} theo mã cổng.
 * Mọi implementation được Spring tự động đăng ký (mặc định gateway "MOCK").
 */
@Component
public class PaymentGatewayFactory {

    public static final String DEFAULT_GATEWAY = "MOCK";

    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::getCode, Function.identity()));
    }

    public PaymentGateway getGateway(String code) {
        String normalized = code != null && !code.isBlank() ? code.toUpperCase() : DEFAULT_GATEWAY;
        PaymentGateway gateway = gateways.get(normalized);
        if (gateway == null) {
            throw new BadRequestException("Cổng thanh toán không được hỗ trợ: " + normalized);
        }
        return gateway;
    }
}
