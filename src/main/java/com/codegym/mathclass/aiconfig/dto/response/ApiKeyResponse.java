package com.codegym.mathclass.aiconfig.dto.response;

import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {
    private Long id;
    private String name;
    private String maskedApiKey;
    private Integer priority;
    private ApiKeyStatus status;
    private LocalDateTime lastUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
