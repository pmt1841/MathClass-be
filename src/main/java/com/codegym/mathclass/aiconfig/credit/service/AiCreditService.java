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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AiCreditService {

    // ---------- Tài khoản ----------
    UserAiAccount getOrCreateAccount(Long userId);

    void grantDefaultForNewUser(Long userId, Role role);

    void backfillExistingUsers();

    // ---------- Reserve / Refund / Adjust ----------
    void reserve(Long userId, String task, int cost);

    void refund(Long userId, String task, int cost);

    /** Hoàn lại credit ước lượng cho tác vụ AI khi người dùng bấm Hủy tiến trình. */
    void refundTaskIfReserved(Long userId, String task);

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

    Page<CreditTransactionResponse> getTransactions(Long userId, CreditTransactionType type, Pageable pageable);

    void recordTransaction(Long userId, int amount, CreditTransactionType type, String task,
                           Long referenceId, String description);

    // ---------- Công thức tính phí theo token (MAT-255 & MAT-354) ----------

    /**
     * Ước lượng số token của nội dung do chính người dùng nhập (user prompt).
     * <p>Loại trừ System Prompt, JSON schema và các chỉ thị hệ thống bổ sung.</p>
     * <p>Tỷ lệ chuẩn hóa ~3.5 ký tự / token cho tiếng Việt và công thức toán.</p>
     */
    static int estimatePromptTokens(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil((double) userPrompt.trim().length() / 3.5));
    }

    /**
     * Số credit đặt chỗ (ước lượng) trước khi gọi AI, dựa trên maxToken của task:
     * {@code max(costPerCall, ceil(maxToken / tokensPerCredit))}.
     * Nếu tokensPerCredit null/0 -> fallback phí cố định costPerCall.
     */
    static int estimateCredits(int maxToken, int costPerCall, Integer tokensPerCredit) {
        return estimateCredits(0, maxToken, costPerCall, tokensPerCredit);
    }

    /**
     * Số credit đặt chỗ (ước lượng) trước khi gọi AI, bao gồm cả token người dùng nhập:
     * {@code max(costPerCall, ceil((userPromptTokens + maxToken) / tokensPerCredit))}.
     */
    static int estimateCredits(Integer userPromptTokens, int maxToken, int costPerCall, Integer tokensPerCredit) {
        int floor = Math.max(0, costPerCall);
        if (tokensPerCredit == null || tokensPerCredit <= 0) {
            return floor;
        }
        int inTokens = (userPromptTokens != null && userPromptTokens > 0) ? userPromptTokens : 0;
        int totalEst = inTokens + Math.max(0, maxToken);
        int byTokens = (int) Math.ceil((double) totalEst / tokensPerCredit);
        return Math.max(floor, byTokens);
    }

    /**
     * Số credit thực tế theo token đầu ra:
     * {@code max(costPerCall, ceil(completionTokens / tokensPerCredit))}.
     * Nếu thiếu token hoặc tokensPerCredit null/0 -> fallback phí tối thiểu costPerCall.
     */
    static int computeCredits(Integer completionTokens, int costPerCall, Integer tokensPerCredit) {
        return computeCredits(0, completionTokens, costPerCall, tokensPerCredit);
    }

    /**
     * Số credit thực tế theo tổng token (bao gồm token người dùng nhập + token đầu ra):
     * {@code max(costPerCall, ceil((userPromptTokens + completionTokens) / tokensPerCredit))}.
     * Nếu thiếu token hoặc tokensPerCredit null/0 -> fallback phí tối thiểu costPerCall.
     */
    static int computeCredits(Integer userPromptTokens, Integer completionTokens, int costPerCall, Integer tokensPerCredit) {
        int floor = Math.max(0, costPerCall);
        int inTokens = (userPromptTokens != null && userPromptTokens > 0) ? userPromptTokens : 0;
        int outTokens = (completionTokens != null && completionTokens > 0) ? completionTokens : 0;
        int totalTokens = inTokens + outTokens;
        if (totalTokens <= 0 || tokensPerCredit == null || tokensPerCredit <= 0) {
            return floor;
        }
        int byTokens = (int) Math.ceil((double) totalTokens / tokensPerCredit);
        return Math.max(floor, byTokens);
    }
}
