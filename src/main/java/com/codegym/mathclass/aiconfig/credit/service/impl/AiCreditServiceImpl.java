package com.codegym.mathclass.aiconfig.credit.service.impl;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageCreateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.AiCreditConfigResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditBalanceResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditCostItem;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.DefaultCreditResponse;
import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.entity.AiCreditDefault;
import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransaction;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.entity.UserAiAccount;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditConfigRepository;
import com.codegym.mathclass.aiconfig.credit.repository.AiCreditDefaultRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditPackageRepository;
import com.codegym.mathclass.aiconfig.credit.repository.CreditTransactionRepository;
import com.codegym.mathclass.aiconfig.credit.repository.UserAiAccountRepository;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.InsufficientCreditException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditServiceImpl implements AiCreditService {

    public static final String CACHE_CREDIT_CONFIGS = "ai_credit_configs_cache";
    public static final String CACHE_CREDIT_DEFAULTS = "ai_credit_defaults_cache";
    public static final String CACHE_PACKAGES = "credit_packages_cache";

    private final UserAiAccountRepository userAiAccountRepository;
    private final AiCreditDefaultRepository aiCreditDefaultRepository;
    private final AiCreditConfigRepository aiCreditConfigRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final CreditPackageRepository creditPackageRepository;
    private final UserRepository userRepository;

    // ---------- Tài khoản ----------

    @Override
    @Transactional
    public UserAiAccount getOrCreateAccount(Long userId) {
        return userAiAccountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserAiAccount account = UserAiAccount.builder()
                            .userId(userId)
                            .balance(0)
                            .totalEarned(0)
                            .totalSpent(0)
                            .build();
                    return userAiAccountRepository.save(account);
                });
    }

    @Override
    @Transactional
    public UserAiAccount getAccountForUpdate(Long userId) {
        return userAiAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> getOrCreateAccount(userId));
    }

    @Override
    @Transactional
    public void grantDefaultForNewUser(Long userId, Role role) {
        Role effectiveRole = role != null ? role : Role.STUDENT;
        int defaultCredits = getDefaultCredits(effectiveRole);
        userAiAccountRepository.findByUserId(userId).ifPresentOrElse(
                account -> log.debug("User {} đã có tài khoản credit, bỏ qua cấp mặc định.", userId),
                () -> {
                    UserAiAccount account = UserAiAccount.builder()
                            .userId(userId)
                            .balance(defaultCredits)
                            .totalEarned(defaultCredits)
                            .totalSpent(0)
                            .build();
                    userAiAccountRepository.save(account);
                    recordTransaction(userId, defaultCredits, CreditTransactionType.GRANT_DEFAULT, null, null,
                            "Credit mặc định khi tạo tài khoản");
                    log.info("[AiCreditService] Granted {} default credits to user {} (role {})",
                            defaultCredits, userId, effectiveRole);
                });
    }

    @Override
    @Transactional
    public void backfillExistingUsers() {
        List<User> users = userRepository.findAll();
        int granted = 0;
        for (User user : users) {
            if (userAiAccountRepository.findByUserId(user.getId()).isEmpty()) {
                grantDefaultForNewUser(user.getId(), user.getRole());
                granted++;
            }
        }
        if (granted > 0) {
            log.info("[AiCreditService] Backfilled default credits for {} existing users.", granted);
        }
    }

    // ---------- Reserve / Refund / Adjust ----------

    @Override
    @Transactional
    public void reserve(Long userId, String task, int cost) {
        if (cost <= 0) {
            return;
        }
        UserAiAccount account = getAccountForUpdate(userId);
        if (account.getBalance() < cost) {
            throw new InsufficientCreditException("Bạn đã hết credit AI. Vui lòng mua thêm.");
        }
        account.setBalance(account.getBalance() - cost);
        account.setTotalSpent(account.getTotalSpent() + cost);
        userAiAccountRepository.save(account);
        recordTransaction(userId, -cost, CreditTransactionType.CONSUME, task, null,
                "Tiêu thụ AI cho tác vụ " + task);
    }

    @Override
    @Transactional
    public void refund(Long userId, String task, int cost) {
        if (cost <= 0) {
            return;
        }
        userAiAccountRepository.findByUserIdForUpdate(userId).ifPresent(account -> {
            account.setBalance(account.getBalance() + cost);
            account.setTotalSpent(Math.max(0, account.getTotalSpent() - cost));
            userAiAccountRepository.save(account);
            recordTransaction(userId, cost, CreditTransactionType.REFUND, task, null,
                    "Hoàn credit do hủy hoặc lỗi khi gọi AI tác vụ " + task);
        });
    }

    @Override
    @Transactional
    public void refundTaskIfReserved(Long userId, String task) {
        if (userId == null || task == null || task.isBlank()) {
            return;
        }
        Optional<AiCreditConfig> creditCfg = getCreditConfig(task);
        if (creditCfg.isEmpty() || !Boolean.TRUE.equals(creditCfg.get().getEnabled())) {
            return;
        }
        int costPerCall = creditCfg.get().getCostPerCall() != null ? creditCfg.get().getCostPerCall() : 0;
        Integer tokensPerCredit = creditCfg.get().getTokensPerCredit();
        int maxToken = taskConfigRepository != null
                ? taskConfigRepository.findByTask(task)
                        .map(cfg -> cfg.getMaxToken() != null ? cfg.getMaxToken() : 2048)
                        .orElse(2048)
                : 2048;
        int reserved = AiCreditService.estimateCredits(maxToken, costPerCall, tokensPerCredit);
        if (reserved > 0) {
            refund(userId, task, reserved);
        }
    }

    @Override
    @Transactional
    public void settle(Long userId, String task, int reserved, int actual) {
        if (reserved <= 0) {
            return;
        }
        int excess = reserved - actual;
        if (excess <= 0) {
            return;
        }
        userAiAccountRepository.findByUserIdForUpdate(userId).ifPresent(account -> {
            account.setBalance(account.getBalance() + excess);
            account.setTotalSpent(Math.max(0, account.getTotalSpent() - excess));
            userAiAccountRepository.save(account);
            recordTransaction(userId, excess, CreditTransactionType.REFUND, task, null,
                    "Hoàn phần dư credit theo token đầu ra cho tác vụ " + task);
        });
    }

    @Override
    @Transactional
    public void adjustByAdmin(Long userId, int amount, String reason) {
        UserAiAccount account = getAccountForUpdate(userId);
        int newBalance = account.getBalance() + amount;
        if (newBalance < 0) {
            throw new BadRequestException("Số dư credit không được nhỏ hơn 0");
        }
        account.setBalance(newBalance);
        if (amount > 0) {
            account.setTotalEarned(account.getTotalEarned() + amount);
        } else {
            account.setTotalSpent(account.getTotalSpent() + Math.min(-amount, account.getTotalSpent()));
        }
        userAiAccountRepository.save(account);
        recordTransaction(userId, amount, CreditTransactionType.ADMIN_ADJUST, null, null,
                reason != null && !reason.isBlank() ? reason : "Admin điều chỉnh credit");
    }

    // ---------- Cấu hình chi phí theo task ----------

    @Override
    @Cacheable(value = CACHE_CREDIT_CONFIGS, key = "#task")
    public Optional<AiCreditConfig> getCreditConfig(String task) {
        return aiCreditConfigRepository.findByTask(task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiCreditConfigResponse> getAllCreditConfigs() {
        return aiCreditConfigRepository.findAllByOrderByTaskAsc().stream()
                .map(AiCreditConfigResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_CREDIT_CONFIGS, key = "#task")
    public AiCreditConfigResponse updateCreditConfig(String task, int costPerCall, Integer tokensPerCredit, boolean enabled) {
        String normalized = task.toUpperCase();
        AiCreditConfig config = aiCreditConfigRepository.findByTask(normalized)
                .orElseGet(() -> AiCreditConfig.builder().task(normalized).build());
        config.setCostPerCall(costPerCall);
        config.setTokensPerCredit(tokensPerCredit);
        config.setEnabled(enabled);
        return AiCreditConfigResponse.from(aiCreditConfigRepository.save(config));
    }

    // ---------- Credit mặc định theo role ----------

    @Override
    @Cacheable(value = CACHE_CREDIT_DEFAULTS, key = "#role")
    public int getDefaultCredits(Role role) {
        return aiCreditDefaultRepository.findByRole(role)
                .map(AiCreditDefault::getDefaultCredits)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DefaultCreditResponse> getAllDefaults() {
        return aiCreditDefaultRepository.findAllByOrderByRoleAsc().stream()
                .map(DefaultCreditResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_CREDIT_DEFAULTS, key = "#role")
    public DefaultCreditResponse updateDefaultCredits(Role role, int credits) {
        if (role == Role.ADMIN) {
            throw new BadRequestException("Không hỗ trợ cấu hình credit mặc định cho ADMIN");
        }
        AiCreditDefault def = aiCreditDefaultRepository.findByRole(role)
                .orElseGet(() -> AiCreditDefault.builder().role(role).build());
        def.setDefaultCredits(credits);
        return DefaultCreditResponse.from(aiCreditDefaultRepository.save(def));
    }

    // ---------- Gói credit ----------

    @Override
    @Cacheable(value = CACHE_PACKAGES, key = "'enabled'")
    public List<CreditPackageResponse> getEnabledPackages() {
        return creditPackageRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(CreditPackageResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPackageResponse> getAllPackages() {
        return creditPackageRepository.findAllByOrderBySortOrderAsc().stream()
                .map(CreditPackageResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_PACKAGES, allEntries = true)
    public CreditPackageResponse createPackage(CreditPackageCreateRequest request) {
        CreditPackage pkg = CreditPackage.builder()
                .name(request.getName())
                .credits(request.getCredits())
                .price(request.getPrice())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        return CreditPackageResponse.from(creditPackageRepository.save(pkg));
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_PACKAGES, allEntries = true)
    public CreditPackageResponse updatePackage(Long id, CreditPackageUpdateRequest request) {
        CreditPackage pkg = creditPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói credit với ID: " + id));
        pkg.setName(request.getName());
        pkg.setCredits(request.getCredits());
        pkg.setPrice(request.getPrice());
        pkg.setEnabled(request.getEnabled() != null ? request.getEnabled() : pkg.getEnabled());
        pkg.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : pkg.getSortOrder());
        return CreditPackageResponse.from(creditPackageRepository.save(pkg));
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_PACKAGES, allEntries = true)
    public void deletePackage(Long id) {
        CreditPackage pkg = creditPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói credit với ID: " + id));
        creditPackageRepository.delete(pkg);
    }

    // ---------- Truy vấn cho user / sổ cái ----------

    @Override
    @Transactional
    public CreditBalanceResponse getMyCreditInfo(Long userId) {
        UserAiAccount account = getOrCreateAccount(userId);
        List<CreditCostItem> costs = aiCreditConfigRepository.findAllByOrderByTaskAsc().stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .map(config -> CreditCostItem.builder()
                        .task(config.getTask())
                        .costPerCall(config.getCostPerCall())
                        .tokensPerCredit(config.getTokensPerCredit())
                        .build())
                .collect(Collectors.toList());
        return CreditBalanceResponse.builder()
                .userId(userId)
                .balance(account.getBalance())
                .totalEarned(account.getTotalEarned())
                .totalSpent(account.getTotalSpent())
                .costs(costs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditTransactionResponse> getTransactions(Long userId, CreditTransactionType type) {
        List<CreditTransaction> transactions;
        if (userId != null && type != null) {
            transactions = creditTransactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
        } else if (userId != null) {
            transactions = creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else if (type != null) {
            transactions = creditTransactionRepository.findByTypeOrderByCreatedAtDesc(type);
        } else {
            transactions = creditTransactionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return transactions.stream()
                .map(CreditTransactionResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordTransaction(Long userId, int amount, CreditTransactionType type, String task,
                                  Long referenceId, String description) {
        CreditTransaction transaction = CreditTransaction.builder()
                .userId(userId)
                .amount(amount)
                .type(type)
                .task(task)
                .referenceId(referenceId)
                .description(description)
                .build();
        creditTransactionRepository.save(transaction);
    }
}



