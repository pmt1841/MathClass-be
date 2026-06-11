package com.codegym.mathclass.classroom.repository;

import com.codegym.mathclass.classroom.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByClassCode(String classCode);

    boolean existsByClassCode(String classCode);

    List<Classroom> findByTeacherId(long id);

    List<Classroom> findByStudentsId(long studentId);
}
