package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

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
}
