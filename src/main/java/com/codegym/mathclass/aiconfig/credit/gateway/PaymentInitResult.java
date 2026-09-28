package com.codegym.mathclass.aiconfig.credit.gateway;

/**
 * Kết quả khởi tạo thanh toán từ {@link PaymentGateway#initiate}.
 *
 * @param status            trạng thái phiên: PENDING / SUCCESS / FAILED
 * @param redirectUrl       URL dẫn người dùng sang trang thanh toán (null với Mock gateway)
 * @param qrUrl             URL mã QR VietQR (chuẩn Napas247)
 * @param transferSyntax    cú pháp nội dung chuyển khoản (ví dụ: MAT 102)
 * @param bankCode          mã ngân hàng (ví dụ: MB, VCB)
 * @param accountNumber     số tài khoản thụ hưởng
 * @param accountHolderName tên chủ tài khoản thụ hưởng
 * @param message           thông điệp phụ
 */
public record PaymentInitResult(
        String status,
        String redirectUrl,
        String qrUrl,
        String transferSyntax,
        String bankCode,
        String accountNumber,
        String accountHolderName,
        String message) {

    public PaymentInitResult(String status, String redirectUrl, String message) {
        this(status, redirectUrl, null, null, null, null, null, message);
    }
}
