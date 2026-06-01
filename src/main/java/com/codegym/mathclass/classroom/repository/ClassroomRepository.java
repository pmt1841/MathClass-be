package com.codegym.mathclass.classroom.repository;

import com.codegym.mathclass.classroom.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByClassCode(String classCode);

    boolean existsByClassCode(String classCode);

    List<Classroom> findByTeacherId(Long id);

    List<Classroom> findByStudentsId(Long studentId);
}
