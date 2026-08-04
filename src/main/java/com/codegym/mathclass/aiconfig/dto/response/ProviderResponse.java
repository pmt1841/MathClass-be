package com.codegym.mathclass.aiconfig.dto.response;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {
    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    private ProviderProtocol protocol;
    private String authHeaderName;
    private String authHeaderPrefix;
    private String authQueryParam;
    private String healthCheckPath;
    private ProviderStrategy strategy;
    private ProviderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
