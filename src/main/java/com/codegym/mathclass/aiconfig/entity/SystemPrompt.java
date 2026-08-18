package com.codegym.mathclass.aiconfig.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_system_prompts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPrompt extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "task_code", nullable = false, length = 50)
    private String taskCode;

    @Column(name = "default_content", nullable = false, columnDefinition = "TEXT")
    private String defaultContent;

    @Column(name = "current_content", nullable = false, columnDefinition = "TEXT")
    private String currentContent;

    @Column(name = "allowed_variables", nullable = false, columnDefinition = "TEXT")
    private String allowedVariables;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SystemPromptStatus status = SystemPromptStatus.ACTIVE;

    public void syncMetadata(String name, String taskCode, String defaultContent, String allowedVariables, String description) {
        this.name = name;
        this.taskCode = taskCode;
        this.defaultContent = defaultContent;
        this.allowedVariables = allowedVariables;
        this.description = description;
        if (this.currentContent == null || this.currentContent.isBlank()) {
            this.currentContent = defaultContent;
        }
    }
}
