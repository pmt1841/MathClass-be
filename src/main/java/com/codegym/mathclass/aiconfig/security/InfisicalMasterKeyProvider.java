package com.codegym.mathclass.aiconfig.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InfisicalMasterKeyProvider implements MasterKeyProvider {

    private final InfisicalClient infisicalClient;
    private volatile String cachedKey;

    @PostConstruct
    public void init() {
        // Eagerly nạp key lúc startup để fail-fast nếu cấu hình Infisical bị lỗi
        getMasterKey();
    }

    @Override
    public String getMasterKey() {
        if (cachedKey == null) {
            synchronized (this) {
                if (cachedKey == null) {
                    log.info("[Infisical] Khởi tạo InfisicalMasterKeyProvider: Tiến hành nạp Master Key từ Infisical...");
                    this.cachedKey = infisicalClient.fetchMasterKey();
                    log.info("[Infisical] Nạp Master Key thành công. Khóa đã được cache an toàn trong bộ nhớ RAM.");
                }
            }
        }
        return cachedKey;
    }
}
