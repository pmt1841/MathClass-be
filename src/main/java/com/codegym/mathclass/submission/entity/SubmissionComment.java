package com.codegym.mathclass.submission.entity;

import com.codegym.mathclass.common.entity.BaseEntity;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "submission_comments")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "quote_text", columnDefinition = "TEXT")
    private String quoteText;

    @Column(name = "occurrence_index")
    private Integer occurrenceIndex;

    @Column(name = "image_code")
    private String imageCode;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
}
