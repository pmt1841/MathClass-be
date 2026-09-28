package com.codegym.mathclass.aiconfig.credit.service;

import com.codegym.mathclass.aiconfig.credit.dto.request.PaymentConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.PaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.PublicPaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;

public interface PaymentConfigService {

    PaymentConfig getPaymentConfig();

    PaymentConfigResponse getConfig();

    PublicPaymentConfigResponse getPublicConfig();

    PaymentConfigResponse updateConfig(PaymentConfigUpdateRequest request);

    PaymentConfigResponse toggleActive(boolean active);

    boolean verifySepayApiKey(String authorizationHeader);
}
