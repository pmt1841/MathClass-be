package com.codegym.mathclass.aiconfig.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_system_prompt_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPromptHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private SystemPrompt prompt;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "change_reason", length = 255)
    private String changeReason;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
}
