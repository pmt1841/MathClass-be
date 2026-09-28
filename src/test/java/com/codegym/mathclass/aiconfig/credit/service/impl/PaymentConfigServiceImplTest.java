package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.request.PaymentConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.PaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.PublicPaymentConfigResponse;
import com.codegym.mathclass.aiconfig.credit.entity.PaymentConfig;
import com.codegym.mathclass.aiconfig.credit.repository.PaymentConfigRepository;
import com.codegym.mathclass.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfigServiceImplTest {

    @Mock
    private PaymentConfigRepository paymentConfigRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PaymentConfigServiceImpl paymentConfigService;

    @Test
    @DisplayName("Should return existing payment config")
    void getConfig_shouldReturnConfig() {
        PaymentConfig config = PaymentConfig.builder()
                .bankCode("VCB")
                .accountNumber("1234567890")
                .accountHolderName("NGUYEN VAN A")
                .sepayApiKey("my-secret-key")
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact2")
                .isActive(true)
                .build();
        config.setId(1L);

        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        PaymentConfigResponse res = paymentConfigService.getConfig();

        assertThat(res.getBankCode()).isEqualTo("VCB");
        assertThat(res.getAccountNumber()).isEqualTo("1234567890");
        assertThat(res.getAccountHolderName()).isEqualTo("NGUYEN VAN A");
        assertThat(res.getSepayApiKey()).contains("****");
        assertThat(res.getHasSepayApiKey()).isTrue();
    }

    @Test
    @DisplayName("Should return public payment config without secret key")
    void getPublicConfig_shouldReturnPublicFields() {
        PaymentConfig config = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("0348714099")
                .accountHolderName("MATHCLASS")
                .sepayApiKey("super-secret")
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact2")
                .isActive(true)
                .build();

        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        PublicPaymentConfigResponse res = paymentConfigService.getPublicConfig();

        assertThat(res.getBankCode()).isEqualTo("MB");
        assertThat(res.getAccountNumber()).isEqualTo("0348714099");
        assertThat(res.getAccountHolderName()).isEqualTo("MATHCLASS");
        assertThat(res.getTransferSyntaxPrefix()).isEqualTo("MAT");
    }

    @Test
    @DisplayName("Should update payment config successfully")
    void updateConfig_shouldSaveAndReturn() {
        PaymentConfig existing = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("111")
                .accountHolderName("OLD NAME")
                .sepayApiKey("old-key")
                .transferSyntaxPrefix("OLD")
                .qrTemplate("compact2")
                .isActive(false)
                .build();

        PaymentConfigUpdateRequest req = PaymentConfigUpdateRequest.builder()
                .bankCode("TCB")
                .accountNumber("9999999999")
                .accountHolderName("LE THI B")
                .sepayApiKey("new-secret")
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact")
                .isActive(true)
                .build();

        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfigResponse res = paymentConfigService.updateConfig(req);

        assertThat(res.getBankCode()).isEqualTo("TCB");
        assertThat(res.getAccountNumber()).isEqualTo("9999999999");
        assertThat(res.getAccountHolderName()).isEqualTo("LE THI B");
        assertThat(res.getSepayApiKey()).contains("****");
        assertThat(res.getHasSepayApiKey()).isTrue();
        assertThat(res.getTransferSyntaxPrefix()).isEqualTo("MAT");
        assertThat(res.getIsActive()).isTrue();
        verify(paymentConfigRepository).save(any(PaymentConfig.class));
    }

    @Test
    @DisplayName("Should preserve existing key when request contains masked key")
    void updateConfig_withMaskedKey_shouldKeepOldKey() {
        PaymentConfig existing = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("111")
                .accountHolderName("OLD NAME")
                .sepayApiKey("secret-super-key-original")
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact2")
                .isActive(true)
                .build();

        PaymentConfigUpdateRequest req = PaymentConfigUpdateRequest.builder()
                .bankCode("MB")
                .accountNumber("111")
                .accountHolderName("OLD NAME")
                .sepayApiKey("secr****************inal") // Masked key
                .transferSyntaxPrefix("MAT")
                .qrTemplate("compact2")
                .isActive(true)
                .build();

        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentConfigService.updateConfig(req);

        assertThat(existing.getSepayApiKey()).isEqualTo("secret-super-key-original");
    }

    @Test
    @DisplayName("verifySepayApiKey should return true when token matches with Apikey prefix")
    void verifySepayApiKey_validApikeyPrefix_shouldReturnTrue() {
        PaymentConfig config = PaymentConfig.builder()
                .sepayApiKey("secret-123456")
                .build();
        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        boolean valid = paymentConfigService.verifySepayApiKey("Apikey secret-123456");
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verifySepayApiKey should return false when key does not match")
    void verifySepayApiKey_mismatchedKey_shouldReturnFalse() {
        PaymentConfig config = PaymentConfig.builder()
                .sepayApiKey("secret-123456")
                .build();
        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        boolean valid = paymentConfigService.verifySepayApiKey("Apikey wrong-key");
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verifySepayApiKey should return false when header is null or empty")
    void verifySepayApiKey_nullHeader_shouldReturnFalse() {
        boolean valid = paymentConfigService.verifySepayApiKey(null);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("toggleActive should update isActive status and broadcast over WebSocket")
    void toggleActive_shouldUpdateAndBroadcast() {
        PaymentConfig config = PaymentConfig.builder()
                .bankCode("MB")
                .accountNumber("0348714099")
                .accountHolderName("MATHCLASS ADMIN")
                .isActive(true)
                .build();
        config.setId(1L);

        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfigResponse res = paymentConfigService.toggleActive(false);

        assertThat(res.getIsActive()).isFalse();
        verify(paymentConfigRepository).save(config);
        verify(messagingTemplate).convertAndSend(eq("/topic/payment-config"), any(PublicPaymentConfigResponse.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when prefix is too short (< 2 chars)")
    void updateConfig_prefixTooShort_shouldThrow() {
        PaymentConfig existing = PaymentConfig.builder().build();
        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        PaymentConfigUpdateRequest req = PaymentConfigUpdateRequest.builder()
                .bankCode("MB")
                .accountNumber("123")
                .accountHolderName("MATH")
                .transferSyntaxPrefix("M")
                .qrTemplate("compact2")
                .build();

        assertThatThrownBy(() -> paymentConfigService.updateConfig(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("từ 2 đến 10 ký tự");
    }

    @Test
    @DisplayName("Should throw BadRequestException when prefix is too long (> 10 chars)")
    void updateConfig_prefixTooLong_shouldThrow() {
        PaymentConfig existing = PaymentConfig.builder().build();
        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        PaymentConfigUpdateRequest req = PaymentConfigUpdateRequest.builder()
                .bankCode("MB")
                .accountNumber("123")
                .accountHolderName("MATH")
                .transferSyntaxPrefix("VERYLONGPREFIXNAME")
                .qrTemplate("compact2")
                .build();

        assertThatThrownBy(() -> paymentConfigService.updateConfig(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("từ 2 đến 10 ký tự");
    }

    @Test
    @DisplayName("Should throw BadRequestException when prefix starts with a number")
    void updateConfig_prefixStartsWithNumber_shouldThrow() {
        PaymentConfig existing = PaymentConfig.builder().build();
        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        PaymentConfigUpdateRequest req = PaymentConfigUpdateRequest.builder()
                .bankCode("MB")
                .accountNumber("123")
                .accountHolderName("MATH")
                .transferSyntaxPrefix("123MAT")
                .qrTemplate("compact2")
                .build();

        assertThatThrownBy(() -> paymentConfigService.updateConfig(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bắt đầu bằng chữ cái");
    }

    @Test
    @DisplayName("Should throw BadRequestException when prefix contains reserved keyword")
    void updateConfig_prefixReserved_shouldThrow() {
        PaymentConfig existing = PaymentConfig.builder().build();
        when(paymentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        PaymentConfigUpdateRequest req = PaymentConfigUpdateRequest.builder()
                .bankCode("MB")
                .accountNumber("123")
                .accountHolderName("MATH")
                .transferSyntaxPrefix("REFUND")
                .qrTemplate("compact2")
                .build();

        assertThatThrownBy(() -> paymentConfigService.updateConfig(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("từ khóa hệ thống bảo lưu");
    }
}
