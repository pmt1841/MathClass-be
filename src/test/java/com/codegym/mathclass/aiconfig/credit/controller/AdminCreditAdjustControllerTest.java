package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditAdjustRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCreditAdjustControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiCreditService aiCreditService;

    @InjectMocks
    private AdminCreditAdjustController adminCreditAdjustController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminCreditAdjustController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Nested
    @DisplayName("POST /admin/credits/adjust Tests")
    class AdjustCreditTests {

        @Test
        @DisplayName("Should adjust credits successfully and return 200 OK")
        void adjustCredit_Success() throws Exception {
            CreditAdjustRequest request = CreditAdjustRequest.builder()
                    .userId(100L)
                    .amount(50)
                    .reason("Admin granted monthly reward")
                    .build();

            doNothing().when(aiCreditService).adjustByAdmin(100L, 50, "Admin granted monthly reward");

            mockMvc.perform(post("/admin/credits/adjust")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Điều chỉnh credit thành công"));

            verify(aiCreditService).adjustByAdmin(100L, 50, "Admin granted monthly reward");
        }

        @Test
        @DisplayName("Should adjust batch credits successfully and return 200 OK")
        void adjustBatchCredit_Success() throws Exception {
            com.codegym.mathclass.aiconfig.credit.dto.request.BatchCreditAdjustRequest request =
                    com.codegym.mathclass.aiconfig.credit.dto.request.BatchCreditAdjustRequest.builder()
                            .userIds(List.of(100L, 101L))
                            .amount(50)
                            .reason("Thưởng thành tích học tập")
                            .build();

            com.codegym.mathclass.aiconfig.credit.dto.response.BatchCreditAdjustResponse response =
                    com.codegym.mathclass.aiconfig.credit.dto.response.BatchCreditAdjustResponse.builder()
                            .total(2)
                            .successCount(2)
                            .failureCount(0)
                            .errors(List.of())
                            .message("Điều chỉnh credit thành công cho 2/2 người dùng")
                            .build();

            when(aiCreditService.adjustBatchByAdmin(List.of(100L, 101L), 50, "Thưởng thành tích học tập"))
                    .thenReturn(response);

            mockMvc.perform(post("/admin/credits/adjust-batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.successCount").value(2))
                    .andExpect(jsonPath("$.message").value("Điều chỉnh credit thành công cho 2/2 người dùng"));

            verify(aiCreditService).adjustBatchByAdmin(List.of(100L, 101L), 50, "Thưởng thành tích học tập");
        }
    }

    @Nested
    @DisplayName("GET /admin/credits/transactions Tests")
    class GetTransactionsTests {

        @Test
        @DisplayName("Should return credit transactions page")
        void getTransactions_Success() throws Exception {
            CreditTransactionResponse txn = CreditTransactionResponse.builder()
                    .id(1L)
                    .userId(100L)
                    .userEmail("student@mathclass.edu.vn")
                    .userRole("STUDENT")
                    .amount(10)
                    .type("ADMIN_ADJUST")
                    .task("BONUS")
                    .description("Weekly bonus")
                    .createdAt(LocalDateTime.now())
                    .build();

            Page<CreditTransactionResponse> page = new PageImpl<>(List.of(txn), PageRequest.of(0, 10), 1);

            when(aiCreditService.getTransactions(eq(100L), eq(CreditTransactionType.ADMIN_ADJUST), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/admin/credits/transactions")
                            .param("userId", "100")
                            .param("type", "ADMIN_ADJUST")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1L))
                    .andExpect(jsonPath("$.content[0].userId").value(100L))
                    .andExpect(jsonPath("$.content[0].amount").value(10))
                    .andExpect(jsonPath("$.content[0].type").value("ADMIN_ADJUST"));
        }
    }
}
