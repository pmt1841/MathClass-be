package com.codegym.mathclass.submission.entity;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.codegym.mathclass.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "submissions")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Submission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "score")
    private Double score;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "teacher_feedback", columnDefinition = "TEXT")
    private String teacherFeedback;

}
