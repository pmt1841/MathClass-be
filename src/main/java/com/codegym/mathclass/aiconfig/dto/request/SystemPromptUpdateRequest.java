package com.codegym.mathclass.aiconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPromptUpdateRequest {

    @NotBlank(message = "Tên prompt không được để trống")
    private String name;

    @NotBlank(message = "Nội dung prompt hiện tại không được để trống")
    private String currentContent;

    private String description;

    private String changeReason;
}
