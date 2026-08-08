package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPurchaseRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPurchaseResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentGatewayFactory;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentInitResult;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentVerifyResult;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.CreditPurchaseService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditPurchaseServiceImpl implements CreditPurchaseService {

    private final CreditPackageRepository creditPackageRepository;
    private final CreditPurchaseOrderRepository creditPurchaseOrderRepository;
    private final UserAiAccountRepository userAiAccountRepository;
    private final AiCreditService aiCreditService;
    private final PaymentGatewayFactory paymentGatewayFactory;

    @Override
    @Transactional
    public CreditPurchaseResponse createPurchase(Long userId, CreditPurchaseRequest request) {
        CreditPackage pkg = creditPackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy gói credit với ID: " + request.getPackageId()));
        if (!Boolean.TRUE.equals(pkg.getEnabled())) {
            throw new BadRequestException("Gói credit này đã bị vô hiệu hóa");
        }

        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(userId)
                .packageId(pkg.getId())
                .credits(pkg.getCredits())
                .price(pkg.getPrice())
                .gatewayCode(PaymentGatewayFactory.DEFAULT_GATEWAY)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order = creditPurchaseOrderRepository.save(order);

        PaymentInitResult init = paymentGatewayFactory.getGateway(order.getGatewayCode()).initiate(order);
        log.info("[CreditPurchase] Created order {} for user {} ({} credits, {} VND)",
                order.getId(), userId, order.getCredits(), order.getPrice());
        return CreditPurchaseResponse.fromOrder(order, init);
    }

    @Override
    @Transactional
    public CreditPurchaseResponse completePurchase(Long userId, Long orderId) {
        CreditPurchaseOrder order = creditPurchaseOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn mua credit với ID: " + orderId));

        if (!Objects.equals(order.getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền xử lý đơn mua credit này");
        }

        // Idempotent: đơn đã thành công thì trả về ngay, không cộng thêm credit (BR-3)
        if (order.getStatus() == CreditPurchaseOrderStatus.SUCCESS) {
            return CreditPurchaseResponse.fromOrder(order, null);
        }

        if (order.getStatus() != CreditPurchaseOrderStatus.PENDING) {
            throw new BadRequestException("Đơn mua credit không ở trạng thái chờ thanh toán");
        }

        PaymentVerifyResult verify = paymentGatewayFactory.getGateway(order.getGatewayCode()).verify(order);
        if (!verify.success()) {
            order.setStatus(CreditPurchaseOrderStatus.FAILED);
            creditPurchaseOrderRepository.save(order);
            throw new BadRequestException("Thanh toán thất bại: " + verify.message());
        }

        order.setStatus(CreditPurchaseOrderStatus.SUCCESS);
        order.setTransactionRef(verify.transactionRef());
        order.setPaidAt(LocalDateTime.now());
        creditPurchaseOrderRepository.save(order);

        UserAiAccount account = userAiAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> aiCreditService.getOrCreateAccount(userId));
        account.setBalance(account.getBalance() + order.getCredits());
        account.setTotalEarned(account.getTotalEarned() + order.getCredits());
        userAiAccountRepository.save(account);

        aiCreditService.recordTransaction(userId, order.getCredits(), CreditTransactionType.PURCHASE, null,
                order.getId(), "Nạp credit từ gói " + order.getCredits() + " credit");

        log.info("[CreditPurchase] Completed order {} for user {}: +{} credits (balance={})",
                order.getId(), userId, order.getCredits(), account.getBalance());

        CreditPurchaseResponse response = CreditPurchaseResponse.fromOrder(order, null);
        response.setCreditsAdded(order.getCredits());
        response.setNewBalance(account.getBalance());
        return response;
    }
}
