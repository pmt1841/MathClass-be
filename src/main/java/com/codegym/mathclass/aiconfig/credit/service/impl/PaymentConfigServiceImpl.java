package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.request.PaymentConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.PaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.PublicPaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import com.codegym.mathclass.aiconfig.credit.repository.PaymentConfigRepository;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private static final Set<String> RESERVED_PREFIXES = Set.of(
            "REFUND", "HOAN", "HOANTIEN", "DUP", "TMP", "SEPAY"
    );

    private final PaymentConfigRepository paymentConfigRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(readOnly = true)
    public PaymentConfig getPaymentConfig() {
        return paymentConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createDefaultConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentConfigResponse getConfig() {
        return PaymentConfigResponse.fromEntity(getPaymentConfig());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPaymentConfigResponse getPublicConfig() {
        return PublicPaymentConfigResponse.fromEntity(getPaymentConfig());
    }

    @Override
    @Transactional
    public PaymentConfigResponse updateConfig(PaymentConfigUpdateRequest request) {
        PaymentConfig config = paymentConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createDefaultConfig);

        config.setBankCode(request.getBankCode().trim().toUpperCase());
        config.setAccountNumber(request.getAccountNumber().trim());
        config.setAccountHolderName(request.getAccountHolderName().trim().toUpperCase());
        if (request.getSepayApiKey() != null) {
            String newKey = request.getSepayApiKey().trim();
            // Chỉ cập nhật nếu người dùng nhập key mới thực tế (không phải chuỗi mask chứa * hoặc •)
            if (StringUtils.hasText(newKey) && !newKey.contains("*") && !newKey.contains("•")) {
                config.setSepayApiKey(newKey);
            }
        }
        String rawPrefix = request.getTransferSyntaxPrefix() != null ? request.getTransferSyntaxPrefix().trim().toUpperCase() : "";
        if (rawPrefix.length() < 2 || rawPrefix.length() > 10) {
            throw new BadRequestException("Tiền tố cú pháp phải từ 2 đến 10 ký tự");
        }
        if (!rawPrefix.matches("^[A-Z][A-Z0-9]*$")) {
            throw new BadRequestException("Tiền tố phải bắt đầu bằng chữ cái và chỉ chứa chữ cái không dấu hoặc chữ số");
        }
        if (RESERVED_PREFIXES.contains(rawPrefix)) {
            throw new BadRequestException("Tiền tố '" + rawPrefix + "' là từ khóa hệ thống bảo lưu, không thể sử dụng");
        }
        config.setTransferSyntaxPrefix(rawPrefix);
        config.setQrTemplate(request.getQrTemplate().trim());
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }

        PaymentConfig saved = paymentConfigRepository.save(config);
        log.info("[PaymentConfig] Cập nhật cấu hình ngân hàng: Bank={}, Account={}, Prefix={}, Active={}",
                saved.getBankCode(), saved.getAccountNumber(), saved.getTransferSyntaxPrefix(), saved.getIsActive());

        broadcastPaymentConfig(saved);

        return PaymentConfigResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public PaymentConfigResponse toggleActive(boolean active) {
        PaymentConfig config = paymentConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createDefaultConfig);
        config.setIsActive(active);
        PaymentConfig saved = paymentConfigRepository.save(config);
        log.info("[PaymentConfig] Thay đổi nhanh trạng thái VietQR: Active={}", saved.getIsActive());

        broadcastPaymentConfig(saved);

        return PaymentConfigResponse.fromEntity(saved);
    }

    private void broadcastPaymentConfig(PaymentConfig config) {
        try {
            PublicPaymentConfigResponse publicConfig = PublicPaymentConfigResponse.fromEntity(config);
            messagingTemplate.convertAndSend("/topic/payment-config", publicConfig);
            log.info("[PaymentConfig] Đã broadcast cấu hình thanh toán qua WebSocket /topic/payment-config (isActive={})", config.getIsActive());
        } catch (Exception e) {
            log.warn("[PaymentConfig] Không thể broadcast WebSocket /topic/payment-config: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifySepayApiKey(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            log.warn("[PaymentConfig] Webhook thiếu Authorization header");
            return false;
        }

        PaymentConfig config = getPaymentConfig();
        String configuredApiKey = config.getSepayApiKey();
        if (!StringUtils.hasText(configuredApiKey)) {
            log.error("[PaymentConfig] Hệ thống chưa cấu hình SePay API Key trong cài đặt Admin");
            return false;
        }

        String token = authorizationHeader.trim().replaceFirst("^(?i)(apikey|bearer)\\s+", "");

        byte[] expectedBytes = configuredApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = token.getBytes(StandardCharsets.UTF_8);
        boolean matched = MessageDigest.isEqual(expectedBytes, actualBytes);
        if (!matched) {
            log.warn("[PaymentConfig] SePay API Key không khớp. Độ dài Token nhận: {}, Độ dài cấu hình: {}",
                    token.length(), configuredApiKey.length());
        }
        return matched;
    }

    private PaymentConfig createDefaultConfig() {
        PaymentConfig config = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("0348714099")
                .accountHolderName("MATHCLASS ADMIN")
                .sepayApiKey("")
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact2")
                .isActive(true)
                .build();
        return paymentConfigRepository.save(config);
    }
}
