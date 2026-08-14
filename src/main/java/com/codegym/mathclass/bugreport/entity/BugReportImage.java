package com.codegym.mathclass.bugreport.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bug_report_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BugReportImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id", nullable = false)
    private BugReport bugReport;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
}
