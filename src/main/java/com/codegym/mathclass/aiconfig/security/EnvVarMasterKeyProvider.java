package com.codegym.mathclass.aiconfig.security;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnvVarMasterKeyProvider implements MasterKeyProvider {

    private final String secretKey;

    public EnvVarMasterKeyProvider(String secretKey) {
        this.secretKey = (secretKey != null && !secretKey.isBlank())
                ? secretKey
                : "MathClassSecretKeyForAiEncryption32B!";
        log.info("[SecretManagement] Khởi tạo EnvVarMasterKeyProvider (Chế độ Local Environment / Properties fallback).");
    }

    @Override
    public String getMasterKey() {
        return secretKey;
    }
}
