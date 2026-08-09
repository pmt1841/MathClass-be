package com.codegym.mathclass.aiconfig.dto.response;

import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPromptResponse {
    private Long id;
    private String code;
    private String name;
    private String taskCode;
    private String defaultContent;
    private String currentContent;
    private List<String> allowedVariables;
    private String description;
    private SystemPromptStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
