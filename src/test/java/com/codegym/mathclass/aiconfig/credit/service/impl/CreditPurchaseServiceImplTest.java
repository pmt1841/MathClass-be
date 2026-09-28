package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPurchaseRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderStatusResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPurchaseResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentGateway;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentGatewayFactory;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentInitResult;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentVerifyResult;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.aiconfig.credit.gateway.SepayPaymentGateway;
import com.codegym.mathclass.common.lock.DistributedLockService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditPurchaseServiceImplTest {

    @Mock
    private CreditPackageRepository creditPackageRepository;

    @Mock
    private CreditPurchaseOrderRepository creditPurchaseOrderRepository;

    @Mock
    private UserAiAccountRepository userAiAccountRepository;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private PaymentGatewayFactory paymentGatewayFactory;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentConfigService paymentConfigService;

    @Mock
    private DistributedLockService distributedLockService;

    @InjectMocks
    private CreditPurchaseServiceImpl creditPurchaseService;

    @BeforeEach
    void setUp() {
        lenient().when(distributedLockService.executeWithLock(anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArgument(3)).run();
            return null;
        }).when(distributedLockService).runWithLock(anyString(), anyLong(), anyLong(), any());

        lenient().when(paymentConfigService.getPaymentConfig()).thenReturn(
                PaymentConfig.builder()
                        .bankCode("MB")
                        .accountNumber("0348714099")
                        .accountHolderName("MATHCLASS ADMIN")
                        .transferSyntaxPrefix("MAT")
                        .qrTemplate("compact2")
                        .isActive(true)
                        .build()
        );
    }

    private CreditPackage package_() {
        CreditPackage pkg = CreditPackage.builder()
                .name("Gói Cơ bản").credits(100).price(20000).enabled(true).sortOrder(1).build();
        pkg.setId(1L);
        return pkg;
    }

    private CreditPurchaseOrder order(long id, long userId, CreditPurchaseOrderStatus status) {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(userId).packageId(1L).credits(100).price(20000)
                .gatewayCode("MOCK").status(status).build();
        order.setId(id);
        return order;
    }

    @Nested
    @DisplayName("createPurchase Tests")
    class CreatePurchaseTests {

        @Test
        @DisplayName("Should create PENDING order and init payment via Sepay gateway when active")
        void createPurchase_shouldCreatePendingOrder() {
            when(creditPackageRepository.findById(1L)).thenReturn(Optional.of(package_()));
            when(paymentGatewayFactory.getGateway(SepayPaymentGateway.GATEWAY_CODE)).thenReturn(paymentGateway);
            when(creditPurchaseOrderRepository.save(any(CreditPurchaseOrder.class)))
                    .thenAnswer(inv -> {
                        CreditPurchaseOrder o = inv.getArgument(0);
                        o.setId(501L);
                        return o;
                    });
            when(paymentGateway.initiate(any(CreditPurchaseOrder.class)))
                    .thenReturn(new PaymentInitResult("PENDING", null, "ok"));

            CreditPurchaseResponse response = creditPurchaseService.createPurchase(42L, new CreditPurchaseRequest(1L));

            assertThat(response.getOrderId()).isEqualTo(501L);
            assertThat(response.getOrderCode()).matches("^\\d{6}0501$");
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getGatewayCode()).isEqualTo(SepayPaymentGateway.GATEWAY_CODE);
            assertThat(response.getCredits()).isEqualTo(100);
            assertThat(response.getPrice()).isEqualTo(20000);
        }

        @Test
        @DisplayName("Should throw BadRequestException when payment gateway is inactive (maintenance)")
        void createPurchase_inactive_shouldThrowBadRequest() {
            when(creditPackageRepository.findById(1L)).thenReturn(Optional.of(package_()));
            when(paymentConfigService.getPaymentConfig()).thenReturn(
                    PaymentConfig.builder()
                            .bankCode("MB")
                            .accountNumber("0348714099")
                            .accountHolderName("MATHCLASS ADMIN")
                            .isActive(false)
                            .build()
            );

            assertThatThrownBy(() -> creditPurchaseService.createPurchase(42L, new CreditPurchaseRequest(1L)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("bảo trì");
        }
    }

    @Nested
    @DisplayName("completePurchase Tests")
    class CompletePurchaseTests {

        @Test
        @DisplayName("UT-BE-09: Should credit balance once on successful payment")
        void completePurchase_success_shouldCreditOnce() {
            CreditPurchaseOrder order = order(501L, 42L, CreditPurchaseOrderStatus.PENDING);
            UserAiAccount account = UserAiAccount.builder().userId(42L).balance(0).totalEarned(0).totalSpent(0).build();

            when(creditPurchaseOrderRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(order));
            when(paymentGatewayFactory.getGateway("MOCK")).thenReturn(paymentGateway);
            when(paymentGateway.verify(any(CreditPurchaseOrder.class)))
                    .thenReturn(new PaymentVerifyResult(true, "MOCK-501", "ok"));
            when(userAiAccountRepository.findByUserIdForUpdate(42L)).thenReturn(Optional.of(account));
            when(userAiAccountRepository.save(any(UserAiAccount.class))).thenAnswer(inv -> inv.getArgument(0));

            CreditPurchaseResponse response = creditPurchaseService.completePurchase(42L, 501L);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            assertThat(response.getCreditsAdded()).isEqualTo(100);
            assertThat(response.getNewBalance()).isEqualTo(100);
            assertThat(account.getBalance()).isEqualTo(100);
            assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.SUCCESS);
            assertThat(order.getTransactionRef()).isEqualTo("MOCK-501");
            verify(aiCreditService).recordTransaction(eq(42L), eq(100), eq(CreditTransactionType.PURCHASE),
                    any(), eq(501L), any());
        }

        @Test
        @DisplayName("Should not credit twice on repeated complete call (idempotent)")
        void completePurchase_idempotent_shouldNotCreditTwice() {
            CreditPurchaseOrder order = order(501L, 42L, CreditPurchaseOrderStatus.SUCCESS);
            when(creditPurchaseOrderRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(order));

            CreditPurchaseResponse response = creditPurchaseService.completePurchase(42L, 501L);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            verify(userAiAccountRepository, never()).save(any());
            verify(aiCreditService, never()).recordTransaction(anyLong(), anyInt(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when attempting to complete SEPAY_VIETQR order directly")
        void completePurchase_sepayVietqr_shouldThrowBadRequest() {
            CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                    .userId(42L).packageId(1L).credits(100).price(20000)
                    .gatewayCode(SepayPaymentGateway.GATEWAY_CODE).status(CreditPurchaseOrderStatus.PENDING).build();
            order.setId(502L);

            when(creditPurchaseOrderRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> creditPurchaseService.completePurchase(42L, 502L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("VietQR");

            assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.PENDING);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException for order of another user")
        void completePurchase_otherUser_shouldThrowAccessDenied() {
            CreditPurchaseOrder order = order(501L, 99L, CreditPurchaseOrderStatus.PENDING);
            when(creditPurchaseOrderRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> creditPurchaseService.completePurchase(42L, 501L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getOrderStatus Tests")
    class GetOrderStatusTests {

        @Test
        @DisplayName("Should return order status and current user balance")
        void getOrderStatus_success() {
            CreditPurchaseOrder order = order(501L, 42L, CreditPurchaseOrderStatus.PENDING);
            UserAiAccount account = UserAiAccount.builder().userId(42L).balance(150).build();

            when(creditPurchaseOrderRepository.findById(501L)).thenReturn(Optional.of(order));
            when(userAiAccountRepository.findByUserId(42L)).thenReturn(Optional.of(account));

            CreditOrderStatusResponse res = creditPurchaseService.getOrderStatus(42L, 501L);

            assertThat(res.getOrderId()).isEqualTo(501L);
            assertThat(res.getStatus()).isEqualTo(CreditPurchaseOrderStatus.PENDING);
            assertThat(res.getNewBalance()).isEqualTo(150);
        }
    }
}
