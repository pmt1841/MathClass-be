package com.codegym.mathclass.aiconfig.credit.service;

import com.codegym.mathclass.aiconfig.credit.dto.request.SepayWebhookRequest;

import java.util.Map;

public interface PaymentWebhookService {

    /**
     * Xử lý biến động số dư từ SePay Webhook (Nạp tiền và Hoàn tiền).
     *
     * @param request Payload webhook gửi từ SePay
     * @return Map chứa kết quả xử lý trả về cho Webhook caller
     */
    Map<String, Object> processSepayWebhook(SepayWebhookRequest request);
}
