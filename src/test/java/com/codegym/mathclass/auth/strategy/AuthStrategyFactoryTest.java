package com.codegym.mathclass.auth.strategy;

import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthStrategyFactoryTest {

    private AuthStrategyFactory factory;
    private AuthStrategy localStrategy;
    private AuthStrategy googleStrategy;

    @BeforeEach
    void setUp() {
        localStrategy = mock(AuthStrategy.class);
        googleStrategy = mock(AuthStrategy.class);

        when(localStrategy.supports(AuthType.LOCAL)).thenReturn(true);
        when(googleStrategy.supports(AuthType.GOOGLE)).thenReturn(true);

        factory = new AuthStrategyFactory(List.of(localStrategy, googleStrategy));
    }

    @Test
    @DisplayName("Should return matching strategy when supported AuthType is passed")
    void getStrategy_SupportedAuthType_ReturnsStrategy() {
        AuthStrategy<?> result = factory.getStrategy(AuthType.LOCAL);
        assertThat(result).isEqualTo(localStrategy);

        AuthStrategy<?> googleResult = factory.getStrategy(AuthType.GOOGLE);
        assertThat(googleResult).isEqualTo(googleStrategy);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when unsupported AuthType is passed")
    void getStrategy_UnsupportedAuthType_ThrowsException() {
        assertThatThrownBy(() -> factory.getStrategy(AuthType.ADMIN_2FA))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy AuthStrategy hỗ trợ phương thức xác thực");
    }
}
