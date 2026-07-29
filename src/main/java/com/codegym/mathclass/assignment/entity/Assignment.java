package com.codegym.mathclass.assignment.entity;

import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;

import com.codegym.mathclass.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;

import lombok.Builder;

@Entity
@Table(name = "assignments")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment extends BaseEntity {

    @Column(nullable = false)
    private String title;

    // Lưu raw text/LaTeX, frontend tự render bằng KaTeX/MathJax
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    // Deadline chỉ được đặt khi giáo viên PUBLISH bài tập
    // Cho phép null khi bài tập đang ở trạng thái DRAFT
    @Column(nullable = true)
    private LocalDateTime deadline;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentVisibility visibility = AssignmentVisibility.PRIVATE;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "original_author_id")
    private User originalAuthor;

    @Column(name = "parent_id")
    private Long parentId;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "assignment_sheet_id")
    private AssignmentSheet assignmentSheet;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "max_score")
    private Double maxScore;

    @Builder.Default
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentDrawing> drawings = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentImage> images = new ArrayList<>();

    @Builder.Default
    @Column(name = "is_reminder_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean isReminderSent = false;
}
