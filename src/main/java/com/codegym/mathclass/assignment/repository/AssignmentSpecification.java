package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentTag;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.List;

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

    public static Specification<Assignment> hasClassCodeOrTeacherDraft(String classCode, boolean isTeacher, long userId) {
        return (root, query, cb) -> {
            Join<Assignment, Classroom> classroomJoin = root.join("classroom", JoinType.LEFT);
            Predicate isClassCode = cb.equal(classroomJoin.get("classCode"), classCode);

            if (isTeacher) {
                Predicate isDraftAndMyTeacher = cb.and(
                        cb.equal(root.get("status"), AssignmentStatus.DRAFT),
                        cb.equal(root.get("teacher").get("id"), userId));
                return cb.or(isClassCode, isDraftAndMyTeacher);
            } else {
                return isClassCode;
            }
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
        return (root, query, cb) -> cb.isNull(root.get("assignmentSheet"));
    }

    public static Specification<Assignment> hasTag(long tagId) {
        return (root, query, cb) -> {
            if (tagId <= 0) return null;
            query.distinct(true);
            Join<Assignment, AssignmentTag> tags = root.join("assignmentTags", JoinType.INNER);
            return cb.equal(tags.get("tag").get("id"), tagId);
        };
    }

    public static Specification<Assignment> hasTagNames(List<String> tagNames) {
        return (root, query, cb) -> {
            if (tagNames == null || tagNames.isEmpty()) return null;
            List<String> cleanNames = tagNames.stream()
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(name -> name.trim().toLowerCase())
                    .toList();
            if (cleanNames.isEmpty()) return null;
            query.distinct(true);
            Join<Assignment, AssignmentTag> tags = root.join("assignmentTags", JoinType.INNER);
            return cb.lower(tags.get("tag").get("name")).in(cleanNames);
        };
    }

    public static Specification<Assignment> hasStudentStatus(long studentId, String studentStatus) {
        return (root, query, cb) -> {
            if (studentStatus == null || studentStatus.isBlank() || "ALL".equalsIgnoreCase(studentStatus)) {
                return null;
            }
            LocalDateTime now = LocalDateTime.now();

            if ("SUBMITTED".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Submission> subRoot = subquery.from(Submission.class);
                subquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(subRoot.get("assignment"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                cb.equal(subRoot.get("status"), SubmissionStatus.SUBMITTED)
                        );
                return cb.exists(subquery);
            }

            if ("GRADED".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Submission> subRoot = subquery.from(Submission.class);
                subquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(subRoot.get("assignment"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                cb.equal(subRoot.get("status"), SubmissionStatus.GRADED)
                        );
                return cb.exists(subquery);
            }

            if ("PENDING".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> submittedOrGradedSubquery = query.subquery(Long.class);
                Root<Submission> subRoot = submittedOrGradedSubquery.from(Submission.class);
                submittedOrGradedSubquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(subRoot.get("assignment"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                subRoot.get("status").in(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED)
                        );
                Predicate notSubmittedOrGraded = cb.not(cb.exists(submittedOrGradedSubquery));
                Predicate notOverdue = cb.or(cb.isNull(root.get("deadline")), cb.greaterThanOrEqualTo(root.get("deadline"), now));
                return cb.and(notSubmittedOrGraded, notOverdue);
            }

            if ("OVERDUE".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> submittedOrGradedSubquery = query.subquery(Long.class);
                Root<Submission> subRoot = submittedOrGradedSubquery.from(Submission.class);
                submittedOrGradedSubquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(subRoot.get("assignment"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                subRoot.get("status").in(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED)
                        );
                Predicate notSubmittedOrGraded = cb.not(cb.exists(submittedOrGradedSubquery));
                Predicate isOverdue = cb.and(cb.isNotNull(root.get("deadline")), cb.lessThan(root.get("deadline"), now));
                return cb.and(notSubmittedOrGraded, isOverdue);
            }

            return null;
        };
    }
}
