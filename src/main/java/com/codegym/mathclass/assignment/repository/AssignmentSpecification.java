package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.codegym.mathclass.assignment.entity.AssignmentSheetItem;
import org.springframework.data.jpa.domain.Specification;

public class AssignmentSpecification {

    public static Specification<Assignment> hasTitleContaining(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return null;
            }
            return cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
        };
    }

    public static Specification<Assignment> hasClassCode(String classCode) {
        return (root, query, cb) -> {
            if (classCode == null || classCode.trim().isEmpty()) {
                return null;
            }
            Join<Assignment, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
            return cb.equal(classroomJoin.get("classCode"), classCode);
        };
    }

    public static Specification<Assignment> hasStatus(AssignmentStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Assignment> isTeacher(long teacherId) {
        return (root, query, cb) -> {
            if (teacherId == 0L) {
                return null;
            }
            return cb.equal(root.get("teacher").get("id"), teacherId);
        };
    }

    public static Specification<Assignment> isStudent(long studentId) {
        return (root, query, cb) -> {
            if (studentId == 0L) {
                return null;
            }
            Join<Assignment, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
            Join<Classroom, User> studentsJoin = classroomJoin.join("students", JoinType.INNER);
            return cb.equal(studentsJoin.get("id"), studentId);
        };
    }

    public static Specification<Assignment> isNotInSheet() {
        return (root, query, cb) -> {
            // EXISTS (SELECT 1 FROM assignment_sheet_items asi WHERE asi.assignment_id = a.id)
            // Or simpler, using a subquery
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<AssignmentSheetItem> subRoot = subquery.from(AssignmentSheetItem.class);
            subquery.select(subRoot.get("assignment").get("id"));
            
            return cb.not(root.get("id").in(subquery));
        };
    }
}
