package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.response.CreditBalanceResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPurchaseResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.credit.service.CreditPurchaseService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CreditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private CreditPurchaseService creditPurchaseService;

    @InjectMocks
    private CreditController creditController;

    private final long userId = 42L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(creditController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private void authenticateAs(long id) {
        CustomUserDetails details = new CustomUserDetails(id, "Nguyễn Văn A", "user@codegym.com",
                "pass", true, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @Nested
    @DisplayName("GET /credits/me Tests")
    class GetMyBalanceTests {

        @Test
        @DisplayName("Should return balance of current user")
        void getMyBalance_success() throws Exception {
            authenticateAs(userId);
            CreditBalanceResponse balance = CreditBalanceResponse.builder()
                    .userId(userId).balance(97).totalEarned(100).totalSpent(3).costs(List.of()).build();
            when(aiCreditService.getMyCreditInfo(userId)).thenReturn(balance);

            mockMvc.perform(get("/credits/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(97))
                    .andExpect(jsonPath("$.userId").value(userId));
        }
    }

    @Nested
    @DisplayName("GET /credits/packages Tests")
    class ListPackagesTests {

        @Test
        @DisplayName("Should return list of enabled packages")
        void listPackages_success() throws Exception {
            authenticateAs(userId);
            when(aiCreditService.getEnabledPackages())
                    .thenReturn(List.of(CreditPackageResponse.builder()
                            .id(1L).name("Gói Cơ bản").credits(100).price(20000).enabled(true).build()));

            mockMvc.perform(get("/credits/packages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Gói Cơ bản"))
                    .andExpect(jsonPath("$[0].credits").value(100));
        }
    }

    @Nested
    @DisplayName("POST /credits/purchase Tests")
    class PurchaseTests {

        @Test
        @DisplayName("Should create purchase order")
        void purchase_shouldCreateOrder() throws Exception {
            authenticateAs(userId);
            CreditPurchaseResponse response = CreditPurchaseResponse.builder()
                    .orderId(501L).gatewayCode("MOCK").status("PENDING")
                    .credits(100).price(20000).build();
            when(creditPurchaseService.createPurchase(eq(userId), any())).thenReturn(response);

            mockMvc.perform(post("/credits/purchase")
                            .contentType("application/json")
                            .content("{\"packageId\": 1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(501L))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Should complete purchase order")
        void purchase_completeOrder() throws Exception {
            authenticateAs(userId);
            CreditPurchaseResponse response = CreditPurchaseResponse.builder()
                    .orderId(501L).gatewayCode("MOCK").status("SUCCESS")
                    .credits(100).price(20000).creditsAdded(100).newBalance(197).build();
            when(creditPurchaseService.completePurchase(eq(userId), eq(501L))).thenReturn(response);

            mockMvc.perform(post("/credits/purchase/501/complete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.creditsAdded").value(100))
                    .andExpect(jsonPath("$.newBalance").value(197));
        }
    }

    @Nested
    @DisplayName("GET /credits/transactions Tests")
    class GetMyTransactionsTests {

        @Test
        @DisplayName("Should return paginated transactions with default size 15")
        void getMyTransactions_defaultPagination() throws Exception {
            authenticateAs(userId);
            CreditTransactionResponse item = CreditTransactionResponse.builder()
                    .id(101L)
                    .userId(userId)
                    .amount(100)
                    .type(CreditTransactionType.PURCHASE.name())
                    .description("Nạp gói Cơ bản")
                    .build();
            when(aiCreditService.getTransactions(eq(userId), eq(null), any()))
                    .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 15), 1));

            mockMvc.perform(get("/credits/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(101L))
                    .andExpect(jsonPath("$.content[0].type").value("PURCHASE"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Should filter transactions by type")
        void getMyTransactions_withTypeFilter() throws Exception {
            authenticateAs(userId);
            CreditTransactionResponse item = CreditTransactionResponse.builder()
                    .id(102L)
                    .userId(userId)
                    .amount(-2)
                    .type(CreditTransactionType.CONSUME.name())
                    .description("Chấm bài AI")
                    .build();
            when(aiCreditService.getTransactions(eq(userId), eq(CreditTransactionType.CONSUME), any()))
                    .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 15), 1));

            mockMvc.perform(get("/credits/transactions")
                            .param("type", "CONSUME")
                            .param("page", "0")
                            .param("size", "15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(102L))
                    .andExpect(jsonPath("$.content[0].type").value("CONSUME"));
        }
    }
}
