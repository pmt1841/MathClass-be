package com.codegym.mathclass.aiconfig.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfigResponse {
    private String task;
    private Long providerId;
    private String model;
    private BigDecimal temperature;
    private Integer maxToken;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
