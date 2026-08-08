package com.codegym.mathclass.aiconfig.credit.dto.response;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditConfigResponse {
    private Long id;
    private String task;
    private Integer costPerCall;
    private Integer tokensPerCredit;
    private Boolean enabled;
    private LocalDateTime updatedAt;

    public static AiCreditConfigResponse from(AiCreditConfig config) {
        return AiCreditConfigResponse.builder()
                .id(config.getId())
                .task(config.getTask())
                .costPerCall(config.getCostPerCall())
                .tokensPerCredit(config.getTokensPerCredit())
                .enabled(config.getEnabled())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
