package com.codegym.mathclass.aiconfig.credit.gateway;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SepayPaymentGatewayTest {

    @Mock
    private PaymentConfigService paymentConfigService;

    @InjectMocks
    private SepayPaymentGateway sepayPaymentGateway;

    private PaymentConfig defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("0348714099")
                .accountHolderName("MATHCLASS")
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact2")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getCode should return SEPAY_VIETQR")
    void getCode_shouldReturnGatewayCode() {
        assertThat(sepayPaymentGateway.getCode()).isEqualTo(SepayPaymentGateway.GATEWAY_CODE);
    }

    @Test
    @DisplayName("initiate should construct syntax using configured prefix and orderCode")
    void initiate_shouldUseConfiguredPrefix() {
        when(paymentConfigService.getPaymentConfig()).thenReturn(defaultConfig);

        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .orderCode("2609280049")
                .price(50000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(49L);

        PaymentInitResult result = sepayPaymentGateway.initiate(order);

        assertThat(result.transferSyntax()).isEqualTo("MAT 2609280049");
        assertThat(result.bankCode()).isEqualTo("MB");
        assertThat(result.accountNumber()).isEqualTo("0348714099");
        assertThat(result.accountHolderName()).isEqualTo("MATHCLASS");
        assertThat(result.qrUrl()).contains("addInfo=MAT+2609280049");
        assertThat(result.qrUrl()).contains("amount=50000");
    }

    @Test
    @DisplayName("initiate should fallback to MAT when transferSyntaxPrefix is blank")
    void initiate_blankPrefix_shouldFallbackToMAT() {
        PaymentConfig blankConfig = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("0348714099")
                .accountHolderName("MATHCLASS")
                .transferSyntaxPrefix("")
                .qrTemplate("compact2")
                .build();
        when(paymentConfigService.getPaymentConfig()).thenReturn(blankConfig);

        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .orderCode("2609280049")
                .price(50000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();

        PaymentInitResult result = sepayPaymentGateway.initiate(order);

        assertThat(result.transferSyntax()).isEqualTo("MAT 2609280049");
    }

    @Test
    @DisplayName("initiate should use custom prefix when configured by admin")
    void initiate_customPrefix_shouldUseCustomPrefix() {
        PaymentConfig customConfig = PaymentConfig.builder()
                .bankCode("VCB")
                .accountNumber("9999")
                .accountHolderName("MATHCLASS CORP")
                .transferSyntaxPrefix("MC")
                .qrTemplate("compact2")
                .build();
        when(paymentConfigService.getPaymentConfig()).thenReturn(customConfig);

        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .orderCode("2609280049")
                .price(100000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();

        PaymentInitResult result = sepayPaymentGateway.initiate(order);

        assertThat(result.transferSyntax()).isEqualTo("MC 2609280049");
        assertThat(result.qrUrl()).contains("addInfo=MC+2609280049");
    }

    @Test
    @DisplayName("verify should return success when order status is SUCCESS")
    void verify_successOrder_shouldReturnSuccess() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .status(CreditPurchaseOrderStatus.SUCCESS)
                .transactionRef("TX-123456")
                .build();

        PaymentVerifyResult result = sepayPaymentGateway.verify(order);

        assertThat(result.success()).isTrue();
        assertThat(result.transactionRef()).isEqualTo("TX-123456");
    }

    @Test
    @DisplayName("verify should return failure when order status is PENDING")
    void verify_pendingOrder_shouldReturnFailure() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();

        PaymentVerifyResult result = sepayPaymentGateway.verify(order);

        assertThat(result.success()).isFalse();
    }
}
