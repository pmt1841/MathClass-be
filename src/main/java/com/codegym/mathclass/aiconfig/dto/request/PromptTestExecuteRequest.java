package com.codegym.mathclass.aiconfig.dto.request;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTestExecuteRequest {

    private String promptCode;

    private String taskCode;

    private String customContent;

    private Map<String, Object> variables;

    private String imageData;

    private String mimeType;
}
