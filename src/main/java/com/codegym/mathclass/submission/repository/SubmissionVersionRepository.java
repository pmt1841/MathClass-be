package com.codegym.mathclass.submission.repository;

import com.codegym.mathclass.submission.entity.SubmissionVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {

    List<SubmissionVersion> findBySubmissionIdOrderByVersionNumberAsc(Long submissionId);

    Optional<SubmissionVersion> findBySubmissionIdAndVersionNumber(Long submissionId, Integer versionNumber);

    Optional<SubmissionVersion> findFirstBySubmissionIdOrderByVersionNumberDesc(Long submissionId);

    @Query("SELECT COALESCE(MAX(sv.versionNumber), 0) FROM SubmissionVersion sv WHERE sv.submission.id = :submissionId")
    int findMaxVersionNumberBySubmissionId(@Param("submissionId") Long submissionId);

    @Query("SELECT sv.submission.id, MAX(sv.versionNumber) FROM SubmissionVersion sv WHERE sv.submission.id IN :submissionIds GROUP BY sv.submission.id")
    List<Object[]> findMaxVersionNumbersBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);
}
