package com.codegym.mathclass.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "storage_cleanup_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageCleanupConfig {

    public static final Long DEFAULT_CONFIG_ID = 1L;

    @Id
    private Long id;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "cron_expression", nullable = false, length = 100)
    @Builder.Default
    private String cronExpression = "0 0 3 * * SUN";

    @Column(name = "grace_period_hours", nullable = false)
    @Builder.Default
    private int gracePeriodHours = 24;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_result_json", columnDefinition = "TEXT")
    private String lastRunResultJson;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
