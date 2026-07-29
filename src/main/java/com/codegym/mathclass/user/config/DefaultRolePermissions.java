package com.codegym.mathclass.user.config;

import com.codegym.mathclass.user.entity.Role;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DefaultRolePermissions {

    private DefaultRolePermissions() {
        // Private constructor to prevent instantiation
    }

    public static final List<String> TEACHER_DEFAULT_PERMISSIONS = List.of(
            "classroom:create", "classroom:update", "classroom:delete", "classroom:manage_requests", "classroom:remove_student",
            "assignment:create", "assignment:update", "assignment:delete", "assignment:publish", "assignment:read",
            "submission:grade", "submission:read_all", "submission:comment",
            "dashboard:teacher_view",
            "library:read", "library:clone"
    );

    public static final List<String> STUDENT_DEFAULT_PERMISSIONS = List.of(
            "classroom:join", "classroom:join_status",
            "assignment:read",
            "submission:submit", "submission:read_own", "submission:comment",
            "dashboard:student_view"
    );

    public static final List<String> ADMIN_DEFAULT_PERMISSIONS = List.of(
            "user:manage",
            "library:read", "library:clone"
    );

    private static final Map<Role, List<String>> DEFAULT_PERMISSIONS_MAP = Map.of(
            Role.TEACHER, TEACHER_DEFAULT_PERMISSIONS,
            Role.STUDENT, STUDENT_DEFAULT_PERMISSIONS,
            Role.ADMIN, ADMIN_DEFAULT_PERMISSIONS
    );

    public static List<String> getDefaultPermissions(Role role) {
        return DEFAULT_PERMISSIONS_MAP.getOrDefault(role, Collections.emptyList());
    }
}
