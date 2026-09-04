package com.codegym.mathclass.aiqueue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJobExecutionResult {

    private Object resultData;
    private Integer actualTokens;
    private Integer actualCredits;
}
