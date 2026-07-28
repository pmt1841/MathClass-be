package com.codegym.mathclass.submission.repository;

import java.time.LocalDateTime;

public interface CompletedStudentProjection {
    long getStudentId();
    String getStudentName();
    String getStudentEmail();
    long getCompletedCount();
    LocalDateTime getLatestSubmittedAt();
    Double getTotalScore();
}
