package com.codegym.mathclass.submission.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "submission_hints", uniqueConstraints = {
        @UniqueConstraint(name = "uk_submission_hint_number", columnNames = {"submission_id", "hint_number"})
}, indexes = {
        @Index(name = "idx_submission_hints_sub_id", columnList = "submission_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionHint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "hint_number", nullable = false)
    private Integer hintNumber;

    @Column(name = "student_content_snapshot", columnDefinition = "TEXT")
    private String studentSnapshotContent;

    @Column(name = "ai_hint_content", columnDefinition = "TEXT", nullable = false)
    private String aiHintContent;

    @Column(name = "prompt_tokens")
    @Builder.Default
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    @Builder.Default
    private Integer completionTokens = 0;
}
