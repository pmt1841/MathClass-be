package com.codegym.mathclass.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandwritingLatexResponse {

    private String latex;
    private String rawAiOutput;
    private Integer completionTokens;
}
