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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "assignment_drawings")
@Data
@EqualsAndHashCode(callSuper = true, exclude = "assignment")
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentDrawing extends BaseEntity {

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(name = "shape_code", length = 50)
    private String shapeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "jsx_graph_data", columnDefinition = "jsonb")
    private Map<String, Object> jsxGraphData;
}
