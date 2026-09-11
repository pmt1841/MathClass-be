package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditTransactionRepositoryTest {

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Test
    @DisplayName("findByUserIdAndTypeOrderByCreatedAtDesc trả về danh sách giao dịch của user theo loại")
    void testFindByUserIdAndTypeOrderByCreatedAtDesc() {
        CreditTransaction txn = CreditTransaction.builder()
                .userId(1L)
                .amount(50)
                .type(CreditTransactionType.PURCHASE)
                .description("Mua gói 50 credits")
                .build();
        txn.setId(100L);

        when(creditTransactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(1L, CreditTransactionType.PURCHASE))
                .thenReturn(List.of(txn));

        List<CreditTransaction> result = creditTransactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(1L, CreditTransactionType.PURCHASE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(50, result.get(0).getAmount());
        assertEquals(CreditTransactionType.PURCHASE, result.get(0).getType());
    }

    @Test
    @DisplayName("findByUserIdAndType với phân trang trả về Page giao dịch")
    void testFindByUserIdAndType_Paged() {
        CreditTransaction txn = CreditTransaction.builder()
                .userId(1L)
                .amount(-2)
                .type(CreditTransactionType.CONSUME)
                .task("QUESTION_GEN")
                .build();
        txn.setId(101L);

        Page<CreditTransaction> page = new PageImpl<>(List.of(txn), PageRequest.of(0, 10), 1);
        when(creditTransactionRepository.findByUserIdAndType(eq(1L), eq(CreditTransactionType.CONSUME), any(PageRequest.class)))
                .thenReturn(page);

        Page<CreditTransaction> result = creditTransactionRepository.findByUserIdAndType(1L, CreditTransactionType.CONSUME, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("QUESTION_GEN", result.getContent().get(0).getTask());
    }

    @Test
    @DisplayName("countUsageByTask thống kê số lượt gọi AI theo từng tác vụ")
    void testCountUsageByTask() {
        Object[] row1 = new Object[]{"QUESTION_GEN", 15L};
        Object[] row2 = new Object[]{"SUBMISSION_GRADING", 30L};

        when(creditTransactionRepository.countUsageByTask(CreditTransactionType.CONSUME))
                .thenReturn(List.of(row1, row2));

        List<Object[]> stats = creditTransactionRepository.countUsageByTask(CreditTransactionType.CONSUME);

        assertNotNull(stats);
        assertEquals(2, stats.size());
        assertEquals("QUESTION_GEN", stats.get(0)[0]);
        assertEquals(15L, stats.get(0)[1]);
        assertEquals("SUBMISSION_GRADING", stats.get(1)[0]);
        assertEquals(30L, stats.get(1)[1]);
    }

    @Test
    @DisplayName("countAiCallsAndFailuresByTaskAndCreatedAtBetween thống kê tổng số lượt gọi và lượt lỗi")
    void testCountAiCallsAndFailuresByTaskAndCreatedAtBetween() {
        Object[] row = new Object[]{"QUESTION_GEN", 100L, 2L}; // 100 calls, 2 failures

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        when(creditTransactionRepository.countAiCallsAndFailuresByTaskAndCreatedAtBetween(eq(start), eq(end)))
                .thenReturn(Collections.singletonList(row));

        List<Object[]> result = creditTransactionRepository.countAiCallsAndFailuresByTaskAndCreatedAtBetween(start, end);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("QUESTION_GEN", result.get(0)[0]);
        assertEquals(100L, result.get(0)[1]);
        assertEquals(2L, result.get(0)[2]);
    }
}
