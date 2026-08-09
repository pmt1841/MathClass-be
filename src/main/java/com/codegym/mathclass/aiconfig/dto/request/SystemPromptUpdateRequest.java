package com.codegym.mathclass.aiconfig.dto.request;

import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Trạng thái không được để trống")
    private SystemPromptStatus status;

    private String changeReason;
}
