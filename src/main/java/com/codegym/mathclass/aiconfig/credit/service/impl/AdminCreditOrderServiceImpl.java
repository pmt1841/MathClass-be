package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.response.CreditOrderAdminResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrder;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPurchaseOrderStatus;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPurchaseOrderRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AdminCreditOrderService;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCreditOrderServiceImpl implements AdminCreditOrderService {

    private final CreditPurchaseOrderRepository creditPurchaseOrderRepository;
    private final UserAiAccountRepository userAiAccountRepository;
    private final UserRepository userRepository;
    private final AiCreditService aiCreditService;
    private final BugReportRepository bugReportRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public Page<CreditOrderAdminResponse> listOrders(CreditPurchaseOrderStatus status, String search, Pageable pageable) {
        Page<CreditPurchaseOrder> pageResult = creditPurchaseOrderRepository.findOrdersForAdmin(status, search, pageable);
        List<CreditPurchaseOrder> orders = pageResult.getContent();

        if (orders.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, pageResult.getTotalElements());
        }

        List<Long> userIds = orders.stream().map(CreditPurchaseOrder::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<String> orderCodes = orders.stream()
                .flatMap(o -> {
                    String full = o.getOrderCode();
                    String base = full.contains("DUP") ? full.substring(0, full.indexOf("DUP")).replace("-", "") : full;
                    return Stream.of(full, base);
                })
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        List<BugReport> bugReports = bugReportRepository.findByOrderCodeIn(orderCodes);
        Map<String, BugReport> bugReportMap = new HashMap<>();
        for (BugReport br : bugReports) {
            if (br.getOrderCode() != null) {
                bugReportMap.putIfAbsent(br.getOrderCode().toUpperCase(), br);
            }
        }

        List<CreditOrderAdminResponse> responses = orders.stream().map(order -> {
            User user = userMap.get(order.getUserId());
            String fullCode = order.getOrderCode() != null ? order.getOrderCode().toUpperCase() : "";
            String baseCode = fullCode.contains("DUP") ? fullCode.substring(0, fullCode.indexOf("DUP")).replace("-", "") : fullCode;
            BugReport matchedReport = bugReportMap.get(fullCode);
            if (matchedReport == null) {
                matchedReport = bugReportMap.get(baseCode);
            }

            return CreditOrderAdminResponse.builder()
                    .orderId(order.getId())
                    .orderCode(order.getOrderCode())
                    .userId(order.getUserId())
                    .userFullName(user != null ? user.getFullName() : null)
                    .userEmail(user != null ? user.getEmail() : null)
                    .packageId(order.getPackageId())
                    .credits(order.getCredits())
                    .price(order.getPrice())
                    .gatewayCode(order.getGatewayCode())
                    .status(order.getStatus())
                    .transactionRef(order.getTransactionRef())
                    .paidAt(order.getPaidAt())
                    .createdAt(order.getCreatedAt())
                    .refundReason(order.getRefundReason())
                    .refundedAt(order.getRefundedAt())
                    .refundRecipientInfo(matchedReport != null ? matchedReport.getDescription() : null)
                    .refundBankCode(matchedReport != null ? matchedReport.getBankCode() : null)
                    .refundAccountNumber(matchedReport != null ? matchedReport.getAccountNumber() : null)
                    .refundAccountName(matchedReport != null ? matchedReport.getAccountHolderName() : null)
                    .bugReportId(matchedReport != null ? matchedReport.getId() : null)
                    .build();
        }).toList();

        return new PageImpl<>(responses, pageable, pageResult.getTotalElements());
    }

    @Override
    @Transactional
    public CreditOrderAdminResponse manualApproveOrder(Long orderId) {
        CreditPurchaseOrder order = creditPurchaseOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn nạp credit với ID: " + orderId));

        if (order.getStatus() == CreditPurchaseOrderStatus.SUCCESS) {
            throw new BadRequestException("Đơn nạp này đã được xử lý thành công trước đó");
        }

        if (order.getStatus() == CreditPurchaseOrderStatus.EXPIRED_PAID
                || order.getStatus() == CreditPurchaseOrderStatus.DUPLICATE_PAYMENT) {
            throw new BadRequestException("Đơn hàng thanh toán lỗi (quá hạn hoặc trùng mã) chỉ có thể hoàn tiền, không thể duyệt cộng credit");
        }

        if (order.getStatus() != CreditPurchaseOrderStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt đơn ở trạng thái chờ thanh toán (PENDING)");
        }

        order.setStatus(CreditPurchaseOrderStatus.SUCCESS);
        order.setTransactionRef("MANUAL-APPROVAL-" + orderId);
        order.setPaidAt(LocalDateTime.now());
        creditPurchaseOrderRepository.save(order);

        UserAiAccount account = userAiAccountRepository.findByUserIdForUpdate(order.getUserId())
                .orElseGet(() -> aiCreditService.getOrCreateAccount(order.getUserId()));
        account.setBalance(account.getBalance() + order.getCredits());
        account.setTotalEarned(account.getTotalEarned() + order.getCredits());
        userAiAccountRepository.save(account);

        aiCreditService.recordTransaction(
                order.getUserId(),
                order.getCredits(),
                CreditTransactionType.PURCHASE,
                null,
                order.getId(),
                "Admin duyệt nạp credit thủ công"
        );

        log.info("[AdminCreditOrder] Admin đã duyệt thủ công đơn {}: +{} credit cho user {}",
                orderId, order.getCredits(), order.getUserId());

        User user = userRepository.findById(order.getUserId()).orElse(null);

        return CreditOrderAdminResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUserId())
                .userFullName(user != null ? user.getFullName() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .packageId(order.getPackageId())
                .credits(order.getCredits())
                .price(order.getPrice())
                .gatewayCode(order.getGatewayCode())
                .status(order.getStatus())
                .transactionRef(order.getTransactionRef())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .refundReason(order.getRefundReason())
                .refundedAt(order.getRefundedAt())
                .build();
    }

    @Override
    @Transactional
    public CreditOrderAdminResponse refundOrder(Long orderId, String refundReason) {
        CreditPurchaseOrder order = creditPurchaseOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn nạp credit với ID: " + orderId));

        if (order.getStatus() == CreditPurchaseOrderStatus.REFUNDED) {
            throw new BadRequestException("Đơn nạp này đã được hoàn tiền trước đó");
        }

        if (order.getStatus() != CreditPurchaseOrderStatus.EXPIRED_PAID
                && order.getStatus() != CreditPurchaseOrderStatus.DUPLICATE_PAYMENT
                && order.getStatus() != CreditPurchaseOrderStatus.PENDING
                && order.getStatus() != CreditPurchaseOrderStatus.FAILED) {
            throw new BadRequestException("Chỉ có thể hoàn tiền cho các đơn thanh toán lỗi (quá hạn hoặc trùng mã)");
        }

        String actualReason = StringUtils.hasText(refundReason)
                ? refundReason.trim()
                : "Admin hoàn tiền cho khách hàng";

        order.setStatus(CreditPurchaseOrderStatus.REFUNDED);
        order.setRefundReason(actualReason);
        order.setRefundedAt(LocalDateTime.now());
        creditPurchaseOrderRepository.save(order);

        log.info("[AdminCreditOrder] Admin đã hoàn tiền cho đơn {}: {}", orderId, actualReason);

        // Gửi thông báo trực tiếp lên chuông Header cho người dùng
        try {
            notificationService.saveAndSendNotification(
                    order.getUserId(),
                    "Đơn nạp " + order.getOrderCode() + " đã được Admin xử lý hoàn tiền thành công.",
                    "/home"
            );
        } catch (Exception e) {
            log.error("[AdminCreditOrder] Lỗi khi gửi thông báo hoàn tiền cho user {}: ", order.getUserId(), e);
        }

        // Tự động giải quyết BugReport nếu có
        String fullCode = order.getOrderCode() != null ? order.getOrderCode().toUpperCase() : "";
        String baseCode = fullCode.contains("DUP") ? fullCode.substring(0, fullCode.indexOf("DUP")).replace("-", "") : fullCode;
        bugReportRepository.findFirstByOrderCodeOrderByCreatedAtDesc(baseCode).ifPresent(br -> {
            br.setStatus(BugReportStatus.RESOLVED);
            bugReportRepository.save(br);
            log.info("[AdminCreditOrder] Đã cập nhật BugReport #{} sang RESOLVED", br.getId());
        });

        User user = userRepository.findById(order.getUserId()).orElse(null);

        return CreditOrderAdminResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUserId())
                .userFullName(user != null ? user.getFullName() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .packageId(order.getPackageId())
                .credits(order.getCredits())
                .price(order.getPrice())
                .gatewayCode(order.getGatewayCode())
                .status(order.getStatus())
                .transactionRef(order.getTransactionRef())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .refundReason(order.getRefundReason())
                .refundedAt(order.getRefundedAt())
                .build();
    }
}
