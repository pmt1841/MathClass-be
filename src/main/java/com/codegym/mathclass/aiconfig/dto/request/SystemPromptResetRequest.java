package com.codegym.mathclass.aiconfig.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPromptResetRequest {
    private String reason;
}
