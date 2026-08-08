package com.codegym.mathclass.aiconfig.credit.gateway;

/**
 * Kết quả xác nhận thanh toán từ {@link PaymentGateway#verify}.
 *
 * @param success         true nếu thanh toán thành công
 * @param transactionRef  mã giao dịch bên phía gateway
 * @param message         thông điệp phụ
 */
public record PaymentVerifyResult(
        boolean success,
        String transactionRef,
        String message) {
}
