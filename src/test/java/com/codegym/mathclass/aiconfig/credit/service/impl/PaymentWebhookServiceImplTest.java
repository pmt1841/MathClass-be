package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.request.SepayWebhookRequest;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImplTest {

    @Mock
    private CreditPurchaseOrderRepository creditPurchaseOrderRepository;

    @Mock
    private PaymentConfigService paymentConfigService;

    @Mock
    private UserAiAccountRepository userAiAccountRepository;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BugReportRepository bugReportRepository;

    @InjectMocks
    private PaymentWebhookServiceImpl paymentWebhookService;

    @BeforeEach
    void setUp() {
        lenient().when(paymentConfigService.getPaymentConfig()).thenReturn(
                PaymentConfig.builder().transferSyntaxPrefix("MAT").build()
        );
    }

    @Test
    @DisplayName("Should process webhook and credit account when valid syntax and sufficient amount")
    void processSepayWebhook_success() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210101")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(101L);

        UserAiAccount account = UserAiAccount.builder()
                .userId(5L)
                .balance(20)
                .totalEarned(20)
                .build();

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210101", "2609210101"))
                .thenReturn(Optional.of(order));
        when(userAiAccountRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(account));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .id(999L)
                .transferType("in")
                .content("Khach hang chuyen tien MAT 2609210101 cam on")
                .transferAmount(20000)
                .referenceCode("FT123456789")
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("orderId", 101L);

        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.SUCCESS);
        assertThat(order.getTransactionRef()).isEqualTo("FT123456789");
        assertThat(account.getBalance()).isEqualTo(120);

        verify(creditPurchaseOrderRepository).save(order);
        verify(userAiAccountRepository).save(account);
        verify(aiCreditService).recordTransaction(eq(5L), eq(100), eq(CreditTransactionType.PURCHASE), any(), eq(101L), any());
    }

    @Test
    @DisplayName("Should process webhook successfully when prefix has no space (e.g. MAT2609210101)")
    void processSepayWebhook_success_prefixWithoutSpace() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210101")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(101L);

        UserAiAccount account = UserAiAccount.builder()
                .userId(5L)
                .balance(20)
                .totalEarned(20)
                .build();

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210101", "2609210101"))
                .thenReturn(Optional.of(order));
        when(userAiAccountRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(account));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .id(999L)
                .transferType("in")
                .content("MAT2609210101")
                .transferAmount(20000)
                .referenceCode("FT123456789")
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", true);
        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.SUCCESS);
        assertThat(account.getBalance()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should process webhook successfully when using custom admin configured prefix (e.g. MC 2609210101)")
    void processSepayWebhook_success_customAdminPrefix() {
        when(paymentConfigService.getPaymentConfig()).thenReturn(
                PaymentConfig.builder().transferSyntaxPrefix("MC").build()
        );

        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210101")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(101L);

        UserAiAccount account = UserAiAccount.builder()
                .userId(5L)
                .balance(20)
                .totalEarned(20)
                .build();

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210101", "2609210101"))
                .thenReturn(Optional.of(order));
        when(userAiAccountRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(account));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .id(999L)
                .transferType("in")
                .content("MC 2609210101")
                .transferAmount(20000)
                .referenceCode("FT123456789")
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", true);
        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.SUCCESS);
        assertThat(account.getBalance()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should be idempotent: return success without adding credits if order already SUCCESS")
    void processSepayWebhook_idempotent_alreadySuccess() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210101")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.SUCCESS)
                .build();
        order.setId(101L);

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210101", "2609210101"))
                .thenReturn(Optional.of(order));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .content("MAT 2609210101")
                .transferAmount(20000)
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("message", "Order already completed");
        verify(userAiAccountRepository, never()).save(any());
        verify(aiCreditService, never()).recordTransaction(anyLong(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should not complete order if transfer amount is insufficient")
    void processSepayWebhook_insufficientAmount() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210101")
                .credits(100)
                .price(50000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(101L);

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210101", "2609210101"))
                .thenReturn(Optional.of(order));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .content("MAT 2609210101")
                .transferAmount(20000) // 20k < 50k
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", false);
        assertThat(result).containsEntry("message", "Insufficient transfer amount");

        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.PENDING);
        verify(userAiAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process outbound refund when transferType is out and valid order found")
    void processSepayWebhook_outboundRefund_success() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("MAT-REFUND123")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.EXPIRED_PAID)
                .build();
        order.setId(101L);

        BugReport bugReport = BugReport.builder()
                .orderCode("MAT-REFUND123")
                .status(BugReportStatus.PENDING)
                .build();
        bugReport.setId(99L);

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("MAT-REFUND123", "MATREFUND123"))
                .thenReturn(Optional.of(order));
        when(bugReportRepository.findFirstByOrderCodeOrderByCreatedAtDesc("MAT-REFUND123"))
                .thenReturn(Optional.of(bugReport));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .transferType("out")
                .content("REFUND MAT-REFUND123")
                .transferAmount(20000)
                .referenceCode("REFUND-TX-999")
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("status", "REFUNDED");

        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.REFUNDED);
        assertThat(order.getTransactionRef()).isEqualTo("REFUND-TX-999");
        assertThat(bugReport.getStatus()).isEqualTo(BugReportStatus.RESOLVED);

        verify(creditPurchaseOrderRepository).save(order);
        verify(bugReportRepository).save(bugReport);
        verify(notificationService).saveAndSendNotification(eq(5L), any(), any());
    }

    @Test
    @DisplayName("Should return Order not found when outbound transfer order does not exist")
    void processSepayWebhook_outboundRefund_orderNotFound() {
        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("UNKNOWN", "UNKNOWN"))
                .thenReturn(Optional.empty());

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .transferType("out")
                .content("REFUND UNKNOWN")
                .transferAmount(20000)
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("message", "Order not found");
    }

    @Test
    @DisplayName("Should create DUPLICATE_PAYMENT order with 4-digit DUP code when paid again")
    void processSepayWebhook_duplicatePayment_createsDupOrderWith4Digits() {
        CreditPurchaseOrder existingOrder = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210048")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.SUCCESS)
                .build();
        existingOrder.setId(48L);

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210048", "2609210048"))
                .thenReturn(Optional.of(existingOrder));
        when(creditPurchaseOrderRepository.existsByTransactionRef("TX-DUP-01")).thenReturn(false);

        ArgumentCaptor<CreditPurchaseOrder> captor = ArgumentCaptor.forClass(CreditPurchaseOrder.class);

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .transferType("in")
                .content("MAT 2609210048")
                .transferAmount(20000)
                .referenceCode("TX-DUP-01")
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", false);
        assertThat(result).containsEntry("message", "Duplicate payment detected. Marked for refund.");

        verify(creditPurchaseOrderRepository).save(captor.capture());
        CreditPurchaseOrder dupOrder = captor.getValue();
        assertThat(dupOrder.getStatus()).isEqualTo(CreditPurchaseOrderStatus.DUPLICATE_PAYMENT);
        assertThat(dupOrder.getOrderCode()).matches("^2609210048DUP\\d{4}$");
    }

    @Test
    @DisplayName("Should recover order from FAILED to SUCCESS when webhook confirms payment")
    void processSepayWebhook_success_recoversFailedOrder() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(5L)
                .packageId(1L)
                .orderCode("2609210048")
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.FAILED)
                .build();
        order.setId(48L);

        UserAiAccount account = UserAiAccount.builder()
                .userId(5L)
                .balance(0)
                .totalEarned(0)
                .build();

        when(creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate("2609210048", "2609210048"))
                .thenReturn(Optional.of(order));
        when(userAiAccountRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(account));

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .id(1002L)
                .transferType("in")
                .content("MAT 2609210048")
                .transferAmount(20000)
                .referenceCode("FT-RECOVER-01")
                .build();

        Map<String, Object> result = paymentWebhookService.processSepayWebhook(request);

        assertThat(result).containsEntry("success", true);
        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.SUCCESS);
        assertThat(account.getBalance()).isEqualTo(100);
    }
}
