package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;

public class AssignmentSheetSpecification {

    /**
     * Chọn Specification phù hợp với role.
     *
     * @throws AccessDeniedException nếu {@code role} không phải TEACHER hoặc STUDENT.
     */
    public static Specification<AssignmentSheet> buildSpecForRole(long userId, String role, String classCode) {
        Role roleEnum;
        try {
            roleEnum = Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Role không hợp lệ: " + role);
        }

        if (roleEnum == Role.TEACHER) {
            return buildTeacherSpec(userId, classCode);
        }
        if (roleEnum == Role.STUDENT) {
            return buildStudentSpec(userId);
        }
        throw new AccessDeniedException("Role không hợp lệ: " + role);
    }

    /**
     * Spec cho giáo viên:
     * <ul>
     *   <li>Khi {@code classCode} null/blank → hiển thị kho cá nhân (classroom IS NULL).</li>
     *   <li>Khi {@code classCode} được cung cấp → hiển thị phiếu thuộc lớp đó.</li>
     * </ul>
     */
    public static Specification<AssignmentSheet> buildTeacherSpec(long teacherId, String classCode) {
        Specification<AssignmentSheet> spec = (root, query, cb) ->
                cb.equal(root.get("teacher").get("id"), teacherId);

        boolean isLibraryView = classCode == null || classCode.isBlank();
        if (isLibraryView) {
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("classroom")));
        } else {
            spec = spec.and((root, query, cb) -> {
                Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
                return cb.equal(classroomJoin.get("classCode"), classCode);
            });
        }
        return spec;
    }

    /**
     * Spec cho học sinh: lấy phiếu thuộc các lớp mà học sinh đang tham gia.
     * Dùng INNER JOIN để loại phiếu không gắn lớp (Master Sheets) ra khỏi kết quả.
     */
    public static Specification<AssignmentSheet> buildStudentSpec(long studentId) {
        return (root, query, cb) -> {
            Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
            Join<Classroom, User> studentsJoin = classroomJoin.join("students", JoinType.INNER);
            return cb.equal(studentsJoin.get("id"), studentId);
        };
    }

    /**
     * Spec tìm kiếm case-insensitive theo tiêu đề phiếu bài tập.
     * Trả về {@code Specification.where(null)} (no-op) nếu keyword rỗng.
     */
    public static Specification<AssignmentSheet> buildKeywordSpec(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }

    /**
     * Spec lọc theo mã lớp, dùng INNER JOIN với classroom.
     * Trả về {@code Specification.where(null)} (no-op) nếu classCode rỗng.
     */
    public static Specification<AssignmentSheet> buildClassCodeSpec(String classCode) {
        if (classCode == null || classCode.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Join<AssignmentSheet, Classroom> classroomJoin = root.join("classroom", JoinType.INNER);
            return cb.equal(classroomJoin.get("classCode"), classCode);
        };
    }

    public static Specification<AssignmentSheet> hasStudentStatus(long studentId, String studentStatus) {
        return (root, query, cb) -> {
            if (studentStatus == null || studentStatus.isBlank() || "ALL".equalsIgnoreCase(studentStatus)) {
                return null;
            }
            LocalDateTime now = LocalDateTime.now();

            if ("SUBMITTED".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Submission> subRoot = subquery.from(Submission.class);
                Join<Submission, Assignment> asgnJoin = subRoot.join("assignment", JoinType.INNER);
                subquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(asgnJoin.get("assignmentSheet"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                subRoot.get("status").in(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED)
                        );
                return cb.exists(subquery);
            }

            if ("GRADED".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> gradedItemSubquery = query.subquery(Long.class);
                Root<Submission> subRoot = gradedItemSubquery.from(Submission.class);
                Join<Submission, Assignment> asgnJoin = subRoot.join("assignment", JoinType.INNER);
                gradedItemSubquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(asgnJoin.get("assignmentSheet"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                cb.equal(subRoot.get("status"), SubmissionStatus.GRADED)
                        );

                Subquery<Long> notGradedItemSubquery = query.subquery(Long.class);
                Root<Assignment> asgnRoot = notGradedItemSubquery.from(Assignment.class);
                Subquery<Long> subGradedSubquery = notGradedItemSubquery.subquery(Long.class);
                Root<Submission> subSubRoot = subGradedSubquery.from(Submission.class);
                subGradedSubquery.select(subSubRoot.get("id"))
                        .where(
                                cb.equal(subSubRoot.get("assignment"), asgnRoot),
                                cb.equal(subSubRoot.get("student").get("id"), studentId),
                                cb.equal(subSubRoot.get("status"), SubmissionStatus.GRADED)
                        );
                notGradedItemSubquery.select(asgnRoot.get("id"))
                        .where(
                                cb.equal(asgnRoot.get("assignmentSheet"), root),
                                cb.not(cb.exists(subGradedSubquery))
                        );

                return cb.and(cb.exists(gradedItemSubquery), cb.not(cb.exists(notGradedItemSubquery)));
            }

            if ("PENDING".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Submission> subRoot = subquery.from(Submission.class);
                Join<Submission, Assignment> asgnJoin = subRoot.join("assignment", JoinType.INNER);
                subquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(asgnJoin.get("assignmentSheet"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                subRoot.get("status").in(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED)
                        );
                Predicate notSubmitted = cb.not(cb.exists(subquery));
                Predicate notOverdue = cb.or(cb.isNull(root.get("deadline")), cb.greaterThanOrEqualTo(root.get("deadline"), now));
                return cb.and(notSubmitted, notOverdue);
            }

            if ("OVERDUE".equalsIgnoreCase(studentStatus)) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Submission> subRoot = subquery.from(Submission.class);
                Join<Submission, Assignment> asgnJoin = subRoot.join("assignment", JoinType.INNER);
                subquery.select(subRoot.get("id"))
                        .where(
                                cb.equal(asgnJoin.get("assignmentSheet"), root),
                                cb.equal(subRoot.get("student").get("id"), studentId),
                                subRoot.get("status").in(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED)
                        );
                Predicate notSubmitted = cb.not(cb.exists(subquery));
                Predicate isOverdue = cb.and(cb.isNotNull(root.get("deadline")), cb.lessThan(root.get("deadline"), now));
                return cb.and(notSubmitted, isOverdue);
            }

            return null;
        };
    }
}
