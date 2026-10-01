package com.codegym.mathclass.assignment.entity;

import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.common.entity.BaseEntity;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignment_sheets")
@Getter
@Setter
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentSheet extends BaseEntity {

    @EqualsAndHashCode.Include
    @Override
    public long getId() {
        return super.getId();
    }

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = true)
    private LocalDateTime deadline;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentVisibility visibility = AssignmentVisibility.PRIVATE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_author_id")
    private User originalAuthor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_sheet_id")
    private AssignmentSheet masterSheet;
    
    @Builder.Default
    @OneToMany(mappedBy = "assignmentSheet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Assignment> items = new ArrayList<>();
}
