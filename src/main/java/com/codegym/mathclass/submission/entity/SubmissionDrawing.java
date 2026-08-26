package com.codegym.mathclass.submission.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "submission_drawings")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDrawing extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Submission submission;

    @Column(name = "shape_code", nullable = false)
    private String shapeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "jsx_graph_data", columnDefinition = "jsonb")
    private Map<String, Object> jsxGraphData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
