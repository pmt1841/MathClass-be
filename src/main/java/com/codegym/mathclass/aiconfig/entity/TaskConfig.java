package com.codegym.mathclass.aiconfig.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ai_task_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskConfig extends BaseEntity {

    @Column(name = "task", nullable = false, unique = true, length = 50)
    private String task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "temperature", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = BigDecimal.valueOf(0.7);

    @Column(name = "max_token", nullable = false)
    @Builder.Default
    private Integer maxToken = 1024;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    public void updateConfig(Provider provider, String model, BigDecimal temperature, Integer maxToken, Boolean enabled) {
        this.provider = provider;
        this.model = model;
        this.temperature = temperature;
        this.maxToken = maxToken;
        if (enabled != null) {
            this.enabled = enabled;
        } else if (this.enabled == null) {
            this.enabled = false;
        }
    }
}
