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
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentGatewayFactory;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentInitResult;
import com.codegym.mathclass.aiconfig.credit.gateway.PaymentVerifyResult;
import com.codegym.mathclass.aiconfig.credit.gateway.SepayPaymentGateway;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.CreditPurchaseService;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.common.lock.DistributedLockService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final PaymentConfigService paymentConfigService;
    private final DistributedLockService distributedLockService;

    private static final DateTimeFormatter DATE_PREFIX_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    @Override
    @Transactional
    public CreditPurchaseResponse createPurchase(Long userId, CreditPurchaseRequest request) {
        CreditPackage pkg = creditPackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy gói credit với ID: " + request.getPackageId()));
        if (!Boolean.TRUE.equals(pkg.getEnabled())) {
            throw new BadRequestException("Gói credit này đã bị vô hiệu hóa");
        }

        PaymentConfig paymentConfig = paymentConfigService.getPaymentConfig();
        if (!Boolean.TRUE.equals(paymentConfig.getIsActive())) {
            throw new BadRequestException("Cổng thanh toán VietQR đang tạm bảo trì, vui lòng quay lại sau.");
        }
        String gatewayCode = SepayPaymentGateway.GATEWAY_CODE;

        // Lưu đơn trước với mã tạm để lấy ID tự tăng duy nhất từ Database
        String tempCode = "TMP" + System.nanoTime();
        CreditPurchaseOrder order = CreditPurchaseOrder.builder()
                .userId(userId)
                .packageId(pkg.getId())
                .credits(pkg.getCredits())
                .price(pkg.getPrice())
                .gatewayCode(gatewayCode)
                .orderCode(tempCode)
                .status(CreditPurchaseOrderStatus.PENDING)
                .build();
        order = creditPurchaseOrderRepository.save(order);

        // Sinh mã đơn chính thức: Thời gian (yyMMdd) + ID tự tăng (tối thiểu 4 chữ số, VD: 2609210049)
        String datePrefix = LocalDate.now().format(DATE_PREFIX_FORMATTER);
        String finalOrderCode = String.format("%s%04d", datePrefix, order.getId());
        order.setOrderCode(finalOrderCode);
        order = creditPurchaseOrderRepository.save(order);

        PaymentInitResult init = paymentGatewayFactory.getGateway(order.getGatewayCode()).initiate(order);
        log.info("[CreditPurchase] Created order {} (code: {}) for user {} ({} credits, {} VND) via gateway {}",
                order.getId(), order.getOrderCode(), userId, order.getCredits(), order.getPrice(), gatewayCode);
        return CreditPurchaseResponse.fromOrder(order, init);
    }

    @Override
    @Transactional
    public CreditPurchaseResponse completePurchase(Long userId, Long orderId) {
        String lockKey = "lock:ai:credit:" + userId;
        return distributedLockService.executeWithLock(lockKey, 5, 10, () -> doCompletePurchase(userId, orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public CreditOrderStatusResponse getOrderStatus(Long userId, Long orderId) {
        CreditPurchaseOrder order = creditPurchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn mua credit với ID: " + orderId));

        if (!Objects.equals(order.getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem đơn mua credit này");
        }

        Integer balance = userAiAccountRepository.findByUserId(userId)
                .map(UserAiAccount::getBalance)
                .orElse(0);

        return CreditOrderStatusResponse.fromOrder(order, balance);
    }

    private CreditPurchaseResponse doCompletePurchase(Long userId, Long orderId) {
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

        // Chặn không cho gọi /complete đối với cổng SEPAY_VIETQR (xử lý tự động qua Webhook hoặc duyệt thủ công từ Admin)
        if (SepayPaymentGateway.GATEWAY_CODE.equals(order.getGatewayCode())) {
            throw new BadRequestException("Cổng thanh toán VietQR tự động xác nhận qua biến động số dư. Không hỗ trợ xác nhận thủ công từ Client.");
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
