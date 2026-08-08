package com.codegym.mathclass.aiconfig.credit.gateway;

/**
 * Kết quả khởi tạo thanh toán từ {@link PaymentGateway#initiate}.
 *
 * @param status      trạng thái phiên: PENDING / SUCCESS / FAILED
 * @param redirectUrl URL dẫn người dùng sang trang thanh toán (null với Mock gateway)
 * @param message     thông điệp phụ
 */
public record PaymentInitResult(
        String status,
        String redirectUrl,
        String message) {
}
