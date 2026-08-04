package com.codegym.mathclass.aiconfig.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_providers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 30)
    @Builder.Default
    private ProviderProtocol protocol = ProviderProtocol.OPENAI_COMPATIBLE;

    @Column(name = "auth_header_name", length = 100)
    private String authHeaderName;

    @Column(name = "auth_header_prefix", length = 50)
    private String authHeaderPrefix;

    @Column(name = "auth_query_param", length = 50)
    private String authQueryParam;

    @Column(name = "health_check_path", length = 255)
    private String healthCheckPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 20)
    @Builder.Default
    private ProviderStrategy strategy = ProviderStrategy.PRIORITY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProviderStatus status = ProviderStatus.ACTIVE;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiKey> apiKeys = new ArrayList<>();
}
