package com.codegym.mathclass.config;

import com.codegym.mathclass.aiconfig.security.EnvVarMasterKeyProvider;
import com.codegym.mathclass.aiconfig.security.InfisicalClient;
import com.codegym.mathclass.aiconfig.security.InfisicalMasterKeyProvider;
import com.codegym.mathclass.aiconfig.security.MasterKeyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MasterKeyProviderConfig {

    @Bean
    @ConditionalOnProperty(name = "mathclass.infisical.enabled", havingValue = "true")
    public MasterKeyProvider infisicalMasterKeyProvider(InfisicalClient infisicalClient) {
        return new InfisicalMasterKeyProvider(infisicalClient);
    }

    @Bean
    @ConditionalOnProperty(name = "mathclass.infisical.enabled", havingValue = "false", matchIfMissing = true)
    public MasterKeyProvider envVarMasterKeyProvider(
            @Value("${app.security.ai-encryption-key:MathClassSecretKeyForAiEncryption32B!}") String fallbackKey) {
        return new EnvVarMasterKeyProvider(fallbackKey);
    }
}
