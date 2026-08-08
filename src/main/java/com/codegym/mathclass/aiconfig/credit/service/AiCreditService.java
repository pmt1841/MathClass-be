package com.codegym.mathclass.aiconfig.credit.service;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageCreateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.AiCreditConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditBalanceResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.DefaultCreditResponse;
import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.user.entity.Role;

import java.util.List;
import java.util.Optional;

public interface AiCreditService {

    // ---------- Tài khoản ----------
    UserAiAccount getOrCreateAccount(Long userId);

    UserAiAccount getAccountForUpdate(Long userId);

    void grantDefaultForNewUser(Long userId, Role role);

    void backfillExistingUsers();

    // ---------- Reserve / Refund / Adjust ----------
    void reserve(Long userId, String task, int cost);

    void refund(Long userId, String task, int cost);

    /** Sau khi AI trả kết quả: hoàn phần dư (reserved - actual) nếu actual nhỏ hơn reserved. */
    void settle(Long userId, String task, int reserved, int actual);

    void adjustByAdmin(Long userId, int amount, String reason);

    // ---------- Cấu hình chi phí theo task ----------
    Optional<AiCreditConfig> getCreditConfig(String task);

    List<AiCreditConfigResponse> getAllCreditConfigs();

    AiCreditConfigResponse updateCreditConfig(String task, int costPerCall, Integer tokensPerCredit, boolean enabled);

    // ---------- Credit mặc định theo role ----------
    int getDefaultCredits(Role role);

    List<DefaultCreditResponse> getAllDefaults();

    DefaultCreditResponse updateDefaultCredits(Role role, int credits);

    // ---------- Gói credit ----------
    List<CreditPackageResponse> getEnabledPackages();

    List<CreditPackageResponse> getAllPackages();

    CreditPackageResponse createPackage(CreditPackageCreateRequest request);

    CreditPackageResponse updatePackage(Long id, CreditPackageUpdateRequest request);

    void deletePackage(Long id);

    // ---------- Truy vấn cho user / sổ cái ----------
    CreditBalanceResponse getMyCreditInfo(Long userId);

    List<CreditTransactionResponse> getTransactions(Long userId, CreditTransactionType type);

    void recordTransaction(Long userId, int amount, CreditTransactionType type, String task,
                           Long referenceId, String description);

    // ---------- Công thức tính phí theo token (MAT-255) ----------

    /**
     * Số credit đặt chỗ (ước lượng) trước khi gọi AI, dựa trên maxToken của task:
     * {@code max(costPerCall, ceil(maxToken / tokensPerCredit))}.
     * Nếu tokensPerCredit null/0 -> fallback phí cố định costPerCall.
     */
    static int estimateCredits(int maxToken, int costPerCall, Integer tokensPerCredit) {
        int floor = Math.max(0, costPerCall);
        if (tokensPerCredit == null || tokensPerCredit <= 0) {
            return floor;
        }
        int byTokens = (int) Math.ceil((double) Math.max(0, maxToken) / tokensPerCredit);
        return Math.max(floor, byTokens);
    }

    /**
     * Số credit thực tế theo token đầu ra:
     * {@code max(costPerCall, ceil(completionTokens / tokensPerCredit))}.
     * Nếu thiếu token hoặc tokensPerCredit null/0 -> fallback phí tối thiểu costPerCall.
     */
    static int computeCredits(Integer completionTokens, int costPerCall, Integer tokensPerCredit) {
        int floor = Math.max(0, costPerCall);
        if (completionTokens == null || completionTokens <= 0) {
            return floor;
        }
        if (tokensPerCredit == null || tokensPerCredit <= 0) {
            return floor;
        }
        int byTokens = (int) Math.ceil((double) completionTokens / tokensPerCredit);
        return Math.max(floor, byTokens);
    }
}
