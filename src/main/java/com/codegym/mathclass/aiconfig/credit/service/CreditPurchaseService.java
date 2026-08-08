package com.codegym.mathclass.aiconfig.credit.service;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPurchaseRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPurchaseResponse;

public interface CreditPurchaseService {

    CreditPurchaseResponse createPurchase(Long userId, CreditPurchaseRequest request);

    CreditPurchaseResponse completePurchase(Long userId, Long orderId);
}
