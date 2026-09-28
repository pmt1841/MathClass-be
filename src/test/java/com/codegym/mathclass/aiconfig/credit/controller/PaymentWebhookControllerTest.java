package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.SepayWebhookRequest;
import com.codegym.mathclass.aiconfig.credit.service.PaymentConfigService;
import com.codegym.mathclass.aiconfig.credit.service.PaymentWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

    @Mock
    private PaymentConfigService paymentConfigService;

    @Mock
    private PaymentWebhookService paymentWebhookService;

    @InjectMocks
    private PaymentWebhookController paymentWebhookController;

    @Test
    @DisplayName("Should return 401 UNAUTHORIZED when Authorization header is invalid")
    void handleSepayWebhook_invalidApiKey_shouldReturn401() {
        when(paymentConfigService.verifySepayApiKey("Apikey wrong-key")).thenReturn(false);

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .content("COBAN 101")
                .transferAmount(20000)
                .build();

        ResponseEntity<Map<String, Object>> response = paymentWebhookController.handleSepayWebhook("Apikey wrong-key", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "Unauthorized API key");
    }

    @Test
    @DisplayName("Should return 200 OK and delegate to service when Authorization header is valid")
    void handleSepayWebhook_validApiKey_shouldReturnOkFromService() {
        when(paymentConfigService.verifySepayApiKey("Apikey valid-key")).thenReturn(true);

        SepayWebhookRequest request = SepayWebhookRequest.builder()
                .content("COBAN 101")
                .transferAmount(20000)
                .build();

        Map<String, Object> serviceResult = Map.of("success", true, "orderId", 101L, "message", "Order completed");
        when(paymentWebhookService.processSepayWebhook(request)).thenReturn(serviceResult);

        ResponseEntity<Map<String, Object>> response = paymentWebhookController.handleSepayWebhook("Apikey valid-key", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResult);
        verify(paymentWebhookService).processSepayWebhook(request);
    }
}
