package com.codegym.mathclass.aiconfig.credit.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_credit_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCreditConfig extends BaseEntity {

    @Column(name = "task", nullable = false, unique = true, length = 50)
    private String task;

    @Column(name = "cost_per_call", nullable = false)
    @Builder.Default
    private Integer costPerCall = 1;

    /** Số token đầu ra tương đương 1 credit. NULL/0 => tính phí cố định cost_per_call. */
    @Column(name = "tokens_per_credit")
    private Integer tokensPerCredit;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
