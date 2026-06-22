package com.codegym.mathclass.assignment.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assignment_images")
@Data
@EqualsAndHashCode(callSuper = true, exclude = "assignment")
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentImage extends BaseEntity {

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(name = "image_code", length = 50, nullable = false)
    private String imageCode;

    @Column(name = "image_url", length = 1000, nullable = false)
    private String imageUrl;
}
