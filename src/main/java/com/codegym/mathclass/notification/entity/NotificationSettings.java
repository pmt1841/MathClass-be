package com.codegym.mathclass.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "master_email", nullable = false)
    @Builder.Default
    private boolean masterEmail = true;

    @Column(name = "teacher_join_request", nullable = false)
    @Builder.Default
    private boolean teacherJoinRequest = true;

    @Column(name = "teacher_new_submission", nullable = false)
    @Builder.Default
    private boolean teacherNewSubmission = true;

    @Column(name = "student_new_assignment", nullable = false)
    @Builder.Default
    private boolean studentNewAssignment = true;

    @Column(name = "student_graded", nullable = false)
    @Builder.Default
    private boolean studentGraded = true;

    @Column(name = "student_deadline_reminder", nullable = false)
    @Builder.Default
    private boolean studentDeadlineReminder = true;
}
