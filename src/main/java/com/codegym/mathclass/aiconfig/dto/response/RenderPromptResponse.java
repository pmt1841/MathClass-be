package com.codegym.mathclass.aiconfig.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenderPromptResponse {
    private String promptCode;
    private String renderedPrompt;
    private List<String> usedVariables;
}
