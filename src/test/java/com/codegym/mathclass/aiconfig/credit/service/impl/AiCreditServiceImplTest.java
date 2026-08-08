package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditDefault;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditConfigRepository;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditDefaultRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditTransactionRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.exception.InsufficientCreditException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCreditServiceImplTest {

    @Mock
    private UserAiAccountRepository userAiAccountRepository;

    @Mock
    private AiCreditDefaultRepository aiCreditDefaultRepository;

    @Mock
    private AiCreditConfigRepository aiCreditConfigRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private CreditPackageRepository creditPackageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AiCreditServiceImpl aiCreditService;

    private final long userId = 42L;

    private UserAiAccount account(int balance, int totalEarned, int totalSpent) {
        UserAiAccount acc = UserAiAccount.builder()
                .userId(userId)
                .balance(balance)
                .totalEarned(totalEarned)
                .totalSpent(totalSpent)
                .build();
        acc.setId(userId);
        return acc;
    }

    @Nested
    @DisplayName("getOrCreateAccount Tests")
    class GetOrCreateAccountTests {

        @Test
        @DisplayName("UT-BE-11: Should return existing account")
        void getOrCreateAccount_existingUser_shouldReturnExisting() {
            UserAiAccount existing = account(10, 100, 0);
            when(userAiAccountRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

            assertThat(aiCreditService.getOrCreateAccount(userId)).isSameAs(existing);
            verify(userAiAccountRepository, never()).save(any(UserAiAccount.class));
        }

        @Test
        @DisplayName("Should create zero-balance account when missing")
        void getOrCreateAccount_missingUser_shouldCreateAccount() {
            when(userAiAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userAiAccountRepository.save(any(UserAiAccount.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UserAiAccount created = aiCreditService.getOrCreateAccount(userId);

            assertThat(created.getBalance()).isZero();
            verify(userAiAccountRepository).save(any(UserAiAccount.class));
        }
    }

    @Nested
    @DisplayName("reserve Tests")
    class ReserveTests {

        @Test
        @DisplayName("UT-BE-01: Should deduct credit and record CONSUME when balance is enough")
        void reserve_enoughBalance_shouldDeductAndRecordConsume() {
            UserAiAccount acc = account(10, 100, 0);
            when(userAiAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(acc));

            aiCreditService.reserve(userId, "STUDENT_HINT", 1);

            assertThat(acc.getBalance()).isEqualTo(9);
            assertThat(acc.getTotalSpent()).isEqualTo(1);

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.CONSUME);
            assertThat(captor.getValue().getAmount()).isEqualTo(-1);
            assertThat(captor.getValue().getTask()).isEqualTo("STUDENT_HINT");
        }

        @Test
        @DisplayName("UT-BE-02: Should throw InsufficientCreditException when balance is less than cost")
        void reserve_insufficientBalance_shouldThrowInsufficientCreditException() {
            UserAiAccount acc = account(0, 100, 0);
            when(userAiAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(acc));

            assertThatThrownBy(() -> aiCreditService.reserve(userId, "STUDENT_HINT", 1))
                    .isInstanceOf(InsufficientCreditException.class)
                    .hasMessageContaining("hết credit");

            verify(creditTransactionRepository, never()).save(any(CreditTransaction.class));
        }

        @Test
        @DisplayName("Should not charge when cost is zero or negative (no config)")
        void reserve_zeroCost_shouldSkip() {
            aiCreditService.reserve(userId, "STUDENT_HINT", 0);

            verify(userAiAccountRepository, never()).findByUserIdForUpdate(anyLong());
            verify(creditTransactionRepository, never()).save(any(CreditTransaction.class));
        }
    }

    @Nested
    @DisplayName("refund Tests")
    class RefundTests {

        @Test
        @DisplayName("UT-BE-03: Should restore balance and record REFUND on AI failure")
        void refund_onAiFailure_shouldRestoreBalanceAndRecordRefund() {
            UserAiAccount acc = account(5, 100, 1);
            when(userAiAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(acc));

            aiCreditService.refund(userId, "STUDENT_HINT", 1);

            assertThat(acc.getBalance()).isEqualTo(6);
            assertThat(acc.getTotalSpent()).isZero();

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.REFUND);
            assertThat(captor.getValue().getAmount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("grantDefault / backfill Tests")
    class GrantTests {

        @Test
        @DisplayName("UT-BE-07: Should create account with role default credits for new user")
        void grantDefault_onRegistration_shouldCreateAccountWithRoleDefault() {
            AiCreditDefault def = AiCreditDefault.builder().role(Role.STUDENT).defaultCredits(100).build();
            when(aiCreditDefaultRepository.findByRole(Role.STUDENT)).thenReturn(Optional.of(def));
            when(userAiAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userAiAccountRepository.save(any(UserAiAccount.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aiCreditService.grantDefaultForNewUser(userId, Role.STUDENT);

            ArgumentCaptor<UserAiAccount> captor = ArgumentCaptor.forClass(UserAiAccount.class);
            verify(userAiAccountRepository).save(captor.capture());
            assertThat(captor.getValue().getBalance()).isEqualTo(100);
            assertThat(captor.getValue().getTotalEarned()).isEqualTo(100);
            verify(creditTransactionRepository).save(any(CreditTransaction.class));
        }

        @Test
        @DisplayName("Should not grant twice when account already exists")
        void grantDefault_existingAccount_shouldSkip() {
            when(userAiAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account(50, 100, 0)));

            aiCreditService.grantDefaultForNewUser(userId, Role.STUDENT);

            verify(userAiAccountRepository, never()).save(any(UserAiAccount.class));
        }

        @Test
        @DisplayName("UT-BE-08: Should backfill default credits only for users without account")
        void backfill_existingUsersWithoutAccount_shouldGrantDefaultsOnce() {
            User student = new User();
            student.setId(1L);
            student.setRole(Role.STUDENT);
            User teacher = new User();
            teacher.setId(2L);
            teacher.setRole(Role.TEACHER);

            when(userRepository.findAll()).thenReturn(List.of(student, teacher));
            when(aiCreditDefaultRepository.findByRole(Role.STUDENT))
                    .thenReturn(Optional.of(AiCreditDefault.builder().role(Role.STUDENT).defaultCredits(100).build()));
            when(aiCreditDefaultRepository.findByRole(Role.TEACHER))
                    .thenReturn(Optional.of(AiCreditDefault.builder().role(Role.TEACHER).defaultCredits(500).build()));
            when(userAiAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());
            when(userAiAccountRepository.findByUserId(2L)).thenReturn(Optional.empty());
            when(userAiAccountRepository.save(any(UserAiAccount.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aiCreditService.backfillExistingUsers();

            verify(userAiAccountRepository, times(2)).save(any(UserAiAccount.class));
        }
    }

    @Nested
    @DisplayName("adjustByAdmin Tests")
    class AdjustByAdminTests {

        @Test
        @DisplayName("Should update balance and record ADMIN_ADJUST transaction")
        void adjustByAdmin_positiveAmount_shouldUpdateBalance() {
            UserAiAccount acc = account(50, 100, 0);
            when(userAiAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(acc));

            aiCreditService.adjustByAdmin(userId, 20, "Hoàn tiền lỗi hệ thống");

            assertThat(acc.getBalance()).isEqualTo(70);
            assertThat(acc.getTotalEarned()).isEqualTo(120);

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.ADMIN_ADJUST);
            assertThat(captor.getValue().getAmount()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("computeCredits / estimateCredits Tests (token-based)")
    class CreditFormulaTests {

        @Test
        @DisplayName("UT-BE-12: Should charge ceil of 3000 tokens / 1000 = 3 credits")
        void computeCredits_3000Tokens_shouldCharge3() {
            assertThat(AiCreditService.computeCredits(3000, 1, 1000)).isEqualTo(3);
        }

        @Test
        @DisplayName("Should charge 2 for 1001 tokens (ceil rounding)")
        void computeCredits_1001Tokens_shouldCharge2() {
            assertThat(AiCreditService.computeCredits(1001, 1, 1000)).isEqualTo(2);
        }

        @Test
        @DisplayName("UT-BE-13: Should respect minimum floor costPerCall")
        void computeCredits_belowFloor_shouldChargeCostPerCall() {
            assertThat(AiCreditService.computeCredits(10, 1, 1000)).isEqualTo(1);
            assertThat(AiCreditService.computeCredits(100, 5, 1000)).isEqualTo(5);
        }

        @Test
        @DisplayName("UT-BE-14: Should fallback to costPerCall when tokens missing")
        void computeCredits_missingTokens_shouldFallbackToCostPerCall() {
            assertThat(AiCreditService.computeCredits(null, 2, 1000)).isEqualTo(2);
            assertThat(AiCreditService.computeCredits(0, 2, 1000)).isEqualTo(2);
        }

        @Test
        @DisplayName("Should fallback to costPerCall when tokensPerCredit null/0")
        void computeCredits_tokensPerCreditUnset_shouldFallback() {
            assertThat(AiCreditService.computeCredits(5000, 3, null)).isEqualTo(3);
            assertThat(AiCreditService.computeCredits(5000, 3, 0)).isEqualTo(3);
        }

        @Test
        @DisplayName("estimateCredits should reserve by maxToken ceiling")
        void estimateCredits_shouldReserveByMaxToken() {
            assertThat(AiCreditService.estimateCredits(1000, 1, 1000)).isEqualTo(1);
            assertThat(AiCreditService.estimateCredits(2500, 1, 1000)).isEqualTo(3);
            assertThat(AiCreditService.estimateCredits(100, 5, 1000)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("settle Tests")
    class SettleTests {

        @Test
        @DisplayName("UT-BE-15: Should refund excess when actual < reserved")
        void settle_actualLessThanEstimate_shouldRefundExcess() {
            UserAiAccount acc = account(50, 100, 3); // reserved=3 đã trừ, totalSpent=3
            when(userAiAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(acc));

            aiCreditService.settle(userId, "STUDENT_HINT", 3, 1);

            assertThat(acc.getBalance()).isEqualTo(52);
            assertThat(acc.getTotalSpent()).isEqualTo(1);

            ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
            verify(creditTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.REFUND);
            assertThat(captor.getValue().getAmount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should not refund when actual >= reserved")
        void settle_actualEqualsReserved_shouldNotRefund() {
            aiCreditService.settle(userId, "STUDENT_HINT", 3, 3);

            verify(creditTransactionRepository, never()).save(any(CreditTransaction.class));
        }
    }
}
