package com.codegym.mathclass.auth.strategy;

import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthStrategyFactory {

    private final List<AuthStrategy<?>> strategies;

    public AuthStrategyFactory(List<AuthStrategy<?>> strategies) {
        this.strategies = strategies;
    }

    @SuppressWarnings("unchecked")
    public <T> AuthStrategy<T> getStrategy(AuthType authType) {
        return (AuthStrategy<T>) strategies.stream()
                .filter(s -> s.supports(authType))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy AuthStrategy hỗ trợ phương thức xác thực: " + authType));
    }
}
