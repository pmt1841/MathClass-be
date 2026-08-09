package com.codegym.mathclass.aiconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenderPromptRequest {

    @NotBlank(message = "Mã prompt code không được để trống")
    private String promptCode;

    private Map<String, Object> variables;
}
