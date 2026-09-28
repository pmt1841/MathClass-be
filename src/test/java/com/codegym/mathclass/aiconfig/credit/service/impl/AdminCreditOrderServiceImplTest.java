package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderAdminResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCreditOrderServiceImplTest {

    @Mock
    private CreditPurchaseOrderRepository creditPurchaseOrderRepository;

    @Mock
    private UserAiAccountRepository userAiAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private BugReportRepository bugReportRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AdminCreditOrderServiceImpl adminCreditOrderService;

    @Test
    @DisplayName("listOrders should delegate to repository findOrdersForAdmin and enrich user info")
    void listOrders_shouldDelegateToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(1L)
                .orderCode("TEST01")
                .packageId(2L)
                .credits(50)
                .price(10000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(101L);

        Page<CreditPurchaseOrder> expectedPage = new PageImpl<>(List.of(order));

        when(creditPurchaseOrderRepository.findOrdersForAdmin(CreditPurchaseOrderStatus.PENDING, null, pageable))
                .thenReturn(expectedPage);

        User user = User.builder().fullName("Học sinh A").email("hs@test.com").build();
        user.setId(1L);
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user));
        when(bugReportRepository.findByOrderCodeIn(any())).thenReturn(List.of());

        Page<CreditOrderAdminResponse> result = adminCreditOrderService.listOrders(CreditPurchaseOrderStatus.PENDING, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOrderId()).isEqualTo(101L);
        assertThat(result.getContent().get(0).getUserFullName()).isEqualTo("Học sinh A");
    }

    @Test
    @DisplayName("manualApproveOrder should update status to SUCCESS, credit balance and record ledger")
    void manualApproveOrder_success() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(10L)
                .packageId(1L)
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order.setId(200L);

        when(creditPurchaseOrderRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(order));

        UserAiAccount account = UserAiAccount.builder()
                .userId(10L)
                .balance(50)
                .totalEarned(50)
                .build();
        when(userAiAccountRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(account));
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        CreditOrderAdminResponse response = adminCreditOrderService.manualApproveOrder(200L);

        assertThat(response.getStatus()).isEqualTo(CreditPurchaseOrderStatus.SUCCESS);
        assertThat(account.getBalance()).isEqualTo(150);
        assertThat(account.getTotalEarned()).isEqualTo(150);
        verify(creditPurchaseOrderRepository).save(order);
        verify(userAiAccountRepository).save(account);
        verify(aiCreditService).recordTransaction(
                eq(10L),
                eq(100),
                eq(CreditTransactionType.PURCHASE),
                any(),
                eq(200L),
                any()
        );
    }

    @Test
    @DisplayName("manualApproveOrder should throw BadRequestException if order is not PENDING")
    void manualApproveOrder_notPending_throws() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(10L)
                .packageId(1L)
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.FAILED)
                .build();
        order.setId(201L);

        when(creditPurchaseOrderRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminCreditOrderService.manualApproveOrder(201L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("manualApproveOrder should throw ResourceNotFoundException if order not found")
    void manualApproveOrder_notFound_throws() {
        when(creditPurchaseOrderRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminCreditOrderService.manualApproveOrder(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("refundOrder should update status to REFUNDED and send notification")
    void refundOrder_success() {
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(10L)
                .orderCode("REF123")
                .packageId(1L)
                .credits(100)
                .price(20000)
                .status(CreditPurchaseOrderStatus.EXPIRED_PAID)
                .build();
        order.setId(202L);

        when(creditPurchaseOrderRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(order));
        when(userRepository.findById(10L)).thenReturn(Optional.empty());
        when(bugReportRepository.findFirstByOrderCodeOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        CreditOrderAdminResponse response = adminCreditOrderService.refundOrder(202L, "Lý do hoàn tiền");

        assertThat(response.getStatus()).isEqualTo(CreditPurchaseOrderStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(CreditPurchaseOrderStatus.REFUNDED);
        assertThat(order.getRefundReason()).isEqualTo("Lý do hoàn tiền");
        verify(creditPurchaseOrderRepository).save(order);
        verify(notificationService).saveAndSendNotification(eq(10L), any(), eq("/home"));
    }
}
