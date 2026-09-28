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
import com.codegym.mathclass.aiconfig.credit.service.PaymentWebhookService;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private static final Pattern REFUND_PATTERN = Pattern.compile(
            "\\b(?:HOANTIEN|HOAN|REFUND)\\b[\\s_\\-]*([A-Za-z0-9\\-_]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{6}\\d{4,}(?:-?DUP\\d+)?)\\b", Pattern.CASE_INSENSITIVE);

    private final CreditPurchaseOrderRepository creditPurchaseOrderRepository;
    private final PaymentConfigService paymentConfigService;
    private final UserAiAccountRepository userAiAccountRepository;
    private final AiCreditService aiCreditService;
    private final NotificationService notificationService;
    private final BugReportRepository bugReportRepository;

    @Override
    @Transactional
    public Map<String, Object> processSepayWebhook(SepayWebhookRequest request) {
        log.info("[PaymentWebhookService] Nhận payload: id={}, gateway={}, type={}, amount={}, content={}",
                request.getId(), request.getGateway(), request.getTransferType(), request.getTransferAmount(), request.getContent());

        boolean isOutbound = request.getTransferType() != null && "out".equalsIgnoreCase(request.getTransferType());

        if (!isOutbound && request.getTransferType() != null && !"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("[PaymentWebhookService] Bỏ qua giao dịch không phải tiền vào/ra (transferType={})", request.getTransferType());
            return Map.of("success", true, "message", "Ignored unknown transfer type");
        }

        if (!StringUtils.hasText(request.getContent())) {
            log.warn("[PaymentWebhookService] Giao dịch không có nội dung chuyển tiền");
            return Map.of("success", true, "message", "Empty transfer content");
        }

        String rawCode = null;
        Matcher refundMatcher = REFUND_PATTERN.matcher(request.getContent());
        if (refundMatcher.find()) {
            rawCode = refundMatcher.group(1).trim().toUpperCase();
        } else {
            PaymentConfig config = paymentConfigService.getPaymentConfig();
            String prefix = (config != null && StringUtils.hasText(config.getTransferSyntaxPrefix()))
                    ? config.getTransferSyntaxPrefix().trim().toUpperCase()
                    : "MAT";

            Pattern prefixPattern = Pattern.compile(
                    "(?i)(?:\\b" + Pattern.quote(prefix) + "\\b|" + Pattern.quote(prefix) + ")[\\s_\\-]*([A-Za-z0-9\\-_]+)");
            Matcher prefixMatcher = prefixPattern.matcher(request.getContent());
            if (prefixMatcher.find()) {
                rawCode = prefixMatcher.group(1).trim().toUpperCase();
            }
        }

        // Fallback: Tìm chuỗi số mã đơn chuẩn ngày tháng (yyMMdd + ID, tối thiểu 10 chữ số, VD: 2609210048)
        if (rawCode == null) {
            Matcher dateMatcher = DATE_PATTERN.matcher(request.getContent());
            if (dateMatcher.find()) {
                rawCode = dateMatcher.group(1).trim().toUpperCase();
            }
        }

        if (rawCode == null) {
            log.warn("[PaymentWebhookService] Không tìm thấy mã đơn hợp lệ trong nội dung: '{}'", request.getContent());
            return Map.of("success", true, "message", "No matching order code found");
        }

        String cleanCode = rawCode.replaceAll("[^A-Za-z0-9]", "");
        Optional<CreditPurchaseOrder> orderOpt = creditPurchaseOrderRepository.findByOrderCodeOrCleanCodeForUpdate(rawCode, cleanCode);

        // Fallback: nếu rawCode có chứa DUP nhưng thiếu dấu '-' (VD: MBBank tự lược bỏ '-' thành 2609210048DUP1234)
        if (orderOpt.isEmpty() && rawCode.contains("DUP") && !rawCode.contains("-DUP")) {
            String withHyphen = rawCode.replace("DUP", "-DUP");
            orderOpt = creditPurchaseOrderRepository.findByOrderCodeForUpdate(withHyphen);
        }

        if (orderOpt.isEmpty()) {
            log.warn("[PaymentWebhookService] Không tìm thấy đơn nạp credit với mã: {}", rawCode);
            return Map.of("success", true, "message", "Order not found");
        }

        CreditPurchaseOrder order = orderOpt.get();

        // Xử lý biến động số dư TIỀN RA (Hoàn tiền cho khách hàng)
        if (isOutbound) {
            log.info("[PaymentWebhookService] Phát hiện biến động tiền ra (Hoàn tiền) cho đơn {}", order.getOrderCode());
            return handleOutboundRefund(request, order);
        }

        Long orderId = order.getId();
        String incomingRef = StringUtils.hasText(request.getReferenceCode())
                ? request.getReferenceCode()
                : (request.getId() != null ? String.valueOf(request.getId()) : null);

        // Trường hợp 1: Đơn đã nạp thành công hoặc đã hoàn tiền -> Nếu khách chuyển lại mã cũ -> DUPLICATE PAYMENT
        if (order.getStatus() == CreditPurchaseOrderStatus.SUCCESS || order.getStatus() == CreditPurchaseOrderStatus.REFUNDED) {
            if (incomingRef == null || incomingRef.equals(order.getTransactionRef())) {
                log.info("[PaymentWebhookService] Đơn {} đã ở trạng thái {} trước đó (Idempotent retry)", orderId, order.getStatus());
                return Map.of("success", true, "message", "Order already completed");
            }

            if (creditPurchaseOrderRepository.existsByTransactionRef(incomingRef)) {
                log.info("[PaymentWebhookService] Giao dịch trùng ref {} đã được ghi nhận trước đó", incomingRef);
                return Map.of("success", true, "message", "Duplicate transaction already recorded");
            }

            int transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : order.getPrice();
            String cleanBaseCode = order.getOrderCode().replace("-", "");
            if (cleanBaseCode.contains("DUP")) {
                cleanBaseCode = cleanBaseCode.substring(0, cleanBaseCode.indexOf("DUP"));
            }
            int random4Digits = ThreadLocalRandom.current().nextInt(10000);
            String dupOrderCode = cleanBaseCode + String.format("DUP%04d", random4Digits);
            for (int retry = 0; retry < 5 && creditPurchaseOrderRepository.existsByOrderCode(dupOrderCode); retry++) {
                random4Digits = ThreadLocalRandom.current().nextInt(10000);
                dupOrderCode = cleanBaseCode + String.format("DUP%04d", random4Digits);
            }

            CreditPurchaseOrder dupOrder = CreditPurchaseOrder.builder()
                    .userId(order.getUserId())
                    .packageId(order.getPackageId())
                    .credits(order.getCredits())
                    .price(transferAmount)
                    .gatewayCode(order.getGatewayCode())
                    .orderCode(dupOrderCode)
                    .status(CreditPurchaseOrderStatus.DUPLICATE_PAYMENT)
                    .transactionRef(incomingRef)
                    .paidAt(LocalDateTime.now())
                    .refundReason("Chuyển khoản trùng mã đơn " + order.getOrderCode() + " đã thanh toán trước đó")
                    .build();
            creditPurchaseOrderRepository.save(dupOrder);

            log.warn("[PaymentWebhookService] Phát hiện chuyển trùng mã đơn {} từ user {}. Đã tạo đơn DUPLICATE_PAYMENT id={}",
                    order.getOrderCode(), order.getUserId(), dupOrder.getId());

            try {
                notificationService.saveAndSendNotification(
                        order.getUserId(),
                        "Đơn nạp " + order.getOrderCode() + " bị trùng mã (đã thanh toán trước đó) nên không được cộng credit. Vui lòng vào Báo cáo sự cố để điền thông tin hoàn tiền.",
                        "/home"
                );
            } catch (Exception e) {
                log.error("[PaymentWebhookService] Lỗi khi gửi thông báo chuyển trùng: ", e);
            }

            return Map.of(
                    "success", false,
                    "message", "Duplicate payment detected. Marked for refund.",
                    "orderId", dupOrder.getId(),
                    "dupOrderCode", dupOrderCode
            );
        }

        // Trường hợp 2: Đơn không ở trạng thái PENDING hoặc FAILED
        if (order.getStatus() != CreditPurchaseOrderStatus.PENDING && order.getStatus() != CreditPurchaseOrderStatus.FAILED) {
            log.warn("[PaymentWebhookService] Đơn {} đang ở trạng thái {} (không thể hoàn tất)", orderId, order.getStatus());
            return Map.of("success", true, "message", "Order not in PENDING or FAILED status");
        }

        if (order.getStatus() == CreditPurchaseOrderStatus.FAILED) {
            log.warn("[PaymentWebhookService] Đơn {} trước đó bị đánh dấu FAILED nhưng ngân hàng đã nhận được tiền. Khôi phục đơn để cộng credit.", orderId);
        }

        // Trường hợp 3: Thanh toán quá hạn 15 phút
        boolean isOverdue = order.getCreatedAt() != null &&
                order.getCreatedAt().plusMinutes(15).isBefore(LocalDateTime.now());

        String finalRef = incomingRef != null ? incomingRef : "SEPAY-" + orderId;

        if (isOverdue) {
            log.warn("[PaymentWebhookService] Đơn {} đã quá hạn 15 phút (tạo lúc {}). Chuyển sang EXPIRED_PAID.",
                    orderId, order.getCreatedAt());

            order.setStatus(CreditPurchaseOrderStatus.EXPIRED_PAID);
            order.setTransactionRef(finalRef);
            order.setPaidAt(LocalDateTime.now());
            order.setRefundReason("Khách hàng thanh toán khi đơn đã quá thời hạn 15 phút");
            creditPurchaseOrderRepository.save(order);

            try {
                notificationService.saveAndSendNotification(
                        order.getUserId(),
                        "Đơn nạp " + order.getOrderCode() + " đã quá hạn 15 phút nên không được cộng credit. Vui lòng vào Báo cáo sự cố để điền thông tin hoàn tiền.",
                        "/home"
                );
            } catch (Exception e) {
                log.error("[PaymentWebhookService] Lỗi khi gửi thông báo quá hạn: ", e);
            }

            return Map.of(
                    "success", false,
                    "message", "Order expired. Marked as EXPIRED_PAID for refund.",
                    "orderId", orderId
            );
        }

        // Trường hợp 4: Kiểm tra số tiền chuyển
        int transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : 0;
        if (transferAmount < order.getPrice()) {
            log.error("[PaymentWebhookService] Số tiền chuyển ({}) nhỏ hơn giá trị đơn hàng ({}) cho đơn {}",
                    transferAmount, order.getPrice(), orderId);
            return Map.of("success", false, "message", "Insufficient transfer amount");
        }

        // Thanh toán hợp lệ trong 15 phút: Cộng credit cho tài khoản
        order.setStatus(CreditPurchaseOrderStatus.SUCCESS);
        order.setTransactionRef(finalRef);
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
                "Nạp credit tự động qua VietQR SePay"
        );

        log.info("[PaymentWebhookService] Xử lý thành công đơn {}: +{} credit cho user {} (Số dư mới: {})",
                orderId, order.getCredits(), order.getUserId(), account.getBalance());

        return Map.of(
                "success", true,
                "message", "Order completed successfully",
                "orderId", orderId,
                "creditsAdded", order.getCredits()
        );
    }

    private Map<String, Object> handleOutboundRefund(SepayWebhookRequest request, CreditPurchaseOrder order) {
        Long orderId = order.getId();

        if (order.getStatus() == CreditPurchaseOrderStatus.REFUNDED) {
            log.info("[PaymentWebhookService] Đơn {} đã ở trạng thái REFUNDED trước đó", orderId);
            return Map.of("success", true, "message", "Order already refunded", "status", "REFUNDED");
        }

        if (order.getStatus() != CreditPurchaseOrderStatus.EXPIRED_PAID
                && order.getStatus() != CreditPurchaseOrderStatus.DUPLICATE_PAYMENT
                && order.getStatus() != CreditPurchaseOrderStatus.PENDING
                && order.getStatus() != CreditPurchaseOrderStatus.FAILED) {
            log.warn("[PaymentWebhookService] Không thể hoàn tiền đơn {} có trạng thái {}", orderId, order.getStatus());
            return Map.of("success", false, "message", "Order status not eligible for refund");
        }

        int transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : 0;
        if (transferAmount > 0 && transferAmount < order.getPrice()) {
            log.warn("[PaymentWebhookService] Số tiền chuyển ra ({}) nhỏ hơn giá trị đơn hàng ({}) cho đơn {}",
                    transferAmount, order.getPrice(), orderId);
            return Map.of("success", false, "message", "Insufficient outbound transfer amount");
        }

        String incomingRef = StringUtils.hasText(request.getReferenceCode())
                ? request.getReferenceCode()
                : (request.getId() != null ? String.valueOf(request.getId()) : "REFUND-OUT-" + System.currentTimeMillis());

        order.setStatus(CreditPurchaseOrderStatus.REFUNDED);
        order.setRefundedAt(LocalDateTime.now());
        order.setTransactionRef(incomingRef);
        order.setRefundReason("Hoàn tiền tự động qua VietQR SePay (Mã GD chuyển ra: " + incomingRef + ")");
        creditPurchaseOrderRepository.save(order);

        log.info("[PaymentWebhookService] Hoàn tiền thành công cho đơn {}: amount={}, ref={}", orderId, transferAmount, incomingRef);

        try {
            notificationService.saveAndSendNotification(
                    order.getUserId(),
                    "Đơn nạp " + order.getOrderCode() + " đã được hoàn tiền thành công về tài khoản ngân hàng của bạn.",
                    "/home"
            );
        } catch (Exception e) {
            log.error("[PaymentWebhookService] Lỗi khi gửi thông báo hoàn tiền cho user {}: ", order.getUserId(), e);
        }

        String fullCode = order.getOrderCode() != null ? order.getOrderCode().toUpperCase() : "";
        String baseCode = fullCode.contains("DUP") ? fullCode.substring(0, fullCode.indexOf("DUP")).replace("-", "") : fullCode;
        bugReportRepository.findFirstByOrderCodeOrderByCreatedAtDesc(baseCode).ifPresent(br -> {
            br.setStatus(BugReportStatus.RESOLVED);
            bugReportRepository.save(br);
            log.info("[PaymentWebhookService] Đã tự động chuyển BugReport #{} sang RESOLVED", br.getId());
        });

        return Map.of(
                "success", true,
                "message", "Refund processed successfully via outbound webhook",
                "orderId", orderId,
                "status", "REFUNDED"
        );
    }
}
