package com.codegym.mathclass.config;

import com.codegym.mathclass.aiconfig.security.EnvVarMasterKeyProvider;
import com.codegym.mathclass.aiconfig.security.InfisicalClient;
import com.codegym.mathclass.aiconfig.security.InfisicalMasterKeyProvider;
import com.codegym.mathclass.aiconfig.security.MasterKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MasterKeyProviderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MasterKeyProviderConfig.class);

    @Test
    @DisplayName("IT-BE-01: Khi mathclass.infisical.enabled=false, inject EnvVarMasterKeyProvider")
    void whenInfisicalDisabled_injectsEnvVarProvider() {
        contextRunner
                .withPropertyValues(
                        "mathclass.infisical.enabled=false",
                        "app.security.ai-encryption-key=TestEnvVarSecretKey32BytesLong!"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MasterKeyProvider.class);
                    assertThat(context).hasSingleBean(EnvVarMasterKeyProvider.class);
                    assertThat(context).doesNotHaveBean(InfisicalMasterKeyProvider.class);

                    MasterKeyProvider provider = context.getBean(MasterKeyProvider.class);
                    assertThat(provider.getMasterKey()).isEqualTo("TestEnvVarSecretKey32BytesLong!");
                });
    }

    @Test
    @DisplayName("IT-BE-02: Khi không cấu hình mathclass.infisical.enabled, mặc định inject EnvVarMasterKeyProvider")
    void whenInfisicalPropertyMissing_injectsEnvVarProviderByDefault() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(MasterKeyProvider.class);
                    assertThat(context).hasSingleBean(EnvVarMasterKeyProvider.class);
                });
    }

    @Test
    @DisplayName("IT-BE-03: Khi mathclass.infisical.enabled=true, inject InfisicalMasterKeyProvider")
    void whenInfisicalEnabled_injectsInfisicalProvider() {
        InfisicalClient mockClient = mock(InfisicalClient.class);

        contextRunner
                .withBean(InfisicalClient.class, () -> mockClient)
                .withPropertyValues("mathclass.infisical.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MasterKeyProvider.class);
                    assertThat(context).hasSingleBean(InfisicalMasterKeyProvider.class);
                    assertThat(context).doesNotHaveBean(EnvVarMasterKeyProvider.class);
                });
    }
}
