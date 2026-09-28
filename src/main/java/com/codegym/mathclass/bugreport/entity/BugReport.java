package com.codegym.mathclass.bugreport.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bug_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BugReport extends BaseEntity {

    @Column(name = "reporter_email", nullable = false)
    private String reporterEmail;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false, length = 50)
    private BugErrorType errorType;

    @Column(name = "order_code", length = 30)
    private String orderCode;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_holder_name", length = 150)
    private String accountHolderName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BugReportStatus status = BugReportStatus.PENDING;

    @Builder.Default
    @OneToMany(mappedBy = "bugReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<BugReportImage> images = new ArrayList<>();
}
