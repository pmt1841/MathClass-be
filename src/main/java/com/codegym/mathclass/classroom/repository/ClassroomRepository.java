package com.codegym.mathclass.classroom.repository;

import com.codegym.mathclass.classroom.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByClassCode(String classCode);

    boolean existsByClassCode(String classCode);

    List<Classroom> findByTeacherId(long id);

    List<Classroom> findByStudentsId(long studentId);

    int countByStudentsId(long studentId);

    int countByTeacherId(long teacherId);

    boolean existsByIdAndStudentsId(long classId, long studentId);

    @Query("SELECT COUNT(DISTINCT s.id) FROM Classroom c JOIN c.students s WHERE c.teacher.id = :teacherId")
    int countDistinctStudentsByTeacherId(@Param("teacherId") long teacherId);
}
