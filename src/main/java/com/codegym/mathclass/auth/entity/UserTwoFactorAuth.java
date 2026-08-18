package com.codegym.mathclass.auth.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_two_factor_auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTwoFactorAuth extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = false;

    @Column(name = "secret_key")
    private String secretKey;

    @Column(name = "temp_secret_key")
    private String tempSecretKey;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
