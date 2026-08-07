package com.codegym.mathclass.aiconfig.strategy;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiProviderStrategyFactory {

    private final List<AiProviderStrategy> strategies;

    public AiProviderStrategy getStrategy(ProviderProtocol protocol) {
        return strategies.stream()
                .filter(s -> s.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Chưa hỗ trợ giao thức AI Provider: " + protocol));
    }
}
