package com.codegym.mathclass.aiconfig.strategy;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;

public interface AiProviderStrategy {
    boolean supports(ProviderProtocol protocol);
    String executePrompt(Provider provider, TaskConfig config, String apiKey, String prompt) throws Exception;
}
