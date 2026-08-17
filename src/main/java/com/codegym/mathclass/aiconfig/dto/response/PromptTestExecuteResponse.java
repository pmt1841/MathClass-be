package com.codegym.mathclass.aiconfig.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTestExecuteResponse {

    private String promptCode;
    private String taskCode;
    private String renderedPrompt;
    private String aiResponse;
    private Long executionTimeMs;
    private String providerCode;
    private String modelName;
    private Integer completionTokens;
    private List<String> usedVariables;
    private boolean success;
    private String errorMessage;
}
