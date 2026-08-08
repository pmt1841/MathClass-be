package com.codegym.mathclass.aiconfig.credit.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import com.codegym.mathclass.user.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_credit_defaults")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCreditDefault extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, unique = true, length = 20)
    private Role role;

    @Column(name = "default_credits", nullable = false)
    @Builder.Default
    private Integer defaultCredits = 0;
}
