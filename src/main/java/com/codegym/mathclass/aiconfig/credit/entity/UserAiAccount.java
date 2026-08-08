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
@Table(name = "user_ai_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAiAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Integer balance = 0;

    @Column(name = "total_earned", nullable = false)
    @Builder.Default
    private Integer totalEarned = 0;

    @Column(name = "total_spent", nullable = false)
    @Builder.Default
    private Integer totalSpent = 0;
}
