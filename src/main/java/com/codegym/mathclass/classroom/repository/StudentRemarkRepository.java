package com.codegym.mathclass.classroom.repository;

import com.codegym.mathclass.classroom.entity.StudentRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRemarkRepository extends JpaRepository<StudentRemark, Long> {

    @Query("SELECT r FROM StudentRemark r " +
           "JOIN FETCH r.teacher t " +
           "JOIN FETCH r.student s " +
           "JOIN r.classroom c " +
           "WHERE c.classCode = :classCode AND s.id = :studentId " +
           "ORDER BY r.createdAt DESC")
    List<StudentRemark> findByClassCodeAndStudentIdOrderByCreatedAtDesc(
            @Param("classCode") String classCode,
            @Param("studentId") Long studentId
    );
}
