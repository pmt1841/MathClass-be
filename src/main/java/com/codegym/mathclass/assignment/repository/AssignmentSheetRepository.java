package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentSheetRepository extends JpaRepository<AssignmentSheet, Long>, JpaSpecificationExecutor<AssignmentSheet> {

    Optional<AssignmentSheet> findFirstByTeacherIdAndTitleAndClassroomIsNull(long teacherId, String title);

    List<AssignmentSheet> findByTeacherIdAndTitle(long teacherId, String title);

    /**
     * Batch query: lấy cặp (title, classCode) của tất cả sheet đã publish vào lớp
     * cho một giáo viên với danh sách tiêu đề cho trước — tránh N+1 trong TEACHER path.
     *
     * @return danh sách Object[]{title (String), classCode (String)}
     */
    @Query("""
            SELECT s.title, c.classCode
            FROM AssignmentSheet s
            JOIN s.classroom c
            WHERE s.teacher.id = :teacherId
            AND s.title IN :titles
            """)
    List<Object[]> findTitleAndClassCodeByTeacherIdAndTitlesIn(
            @Param("teacherId") long teacherId,
            @Param("titles") List<String> titles
    );
}
