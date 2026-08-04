package com.codegym.mathclass.aiconfig.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestConnectionResponse {
    private Boolean success;
    private Boolean valid;
    private Long latencyMs;
    private String message;
    private String errorCode;
}
