package com.codegym.mathclass.auth.entity;

import com.codegym.mathclass.auth.dto.request.AuthType;
import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "auth_audit_logs", indexes = {
        @Index(name = "idx_auth_audit_email", columnList = "email, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAuditLog extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private AuthType authType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}
