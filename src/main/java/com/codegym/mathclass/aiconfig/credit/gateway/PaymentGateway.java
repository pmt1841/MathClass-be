package com.codegym.mathclass.aiconfig.credit.gateway;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;

/**
 * Trừu tượng hóa cổng thanh toán. MVP dùng {@link MockPaymentGateway},
 * sau này thêm VNPay/MoMo/Stripe bằng cách implement interface này mà
 * không phải sửa logic nghiệp vụ {@code CreditPurchaseService}.
 */
public interface PaymentGateway {

    /** Mã cổng thanh toán, ví dụ: "MOCK", "VNPAY", "MOMO". */
    String getCode();

    /** Tạo phiên thanh toán / URL redirect cho đơn hàng. */
    PaymentInitResult initiate(CreditPurchaseOrder order);

    /** Xác nhận kết quả thanh toán của đơn hàng. */
    PaymentVerifyResult verify(CreditPurchaseOrder order);
}
