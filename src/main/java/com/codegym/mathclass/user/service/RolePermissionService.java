package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.PermissionResponse;
import com.codegym.mathclass.user.entity.Role;

import java.util.List;

public interface RolePermissionService {

    List<PermissionResponse> getPermissionsByRole(Role role);

    List<PermissionResponse> getAllPermissions();

    void updateRolePermissions(Role role, List<Long> permissionIds);

    void updateRolePermissions(Role role, List<Long> permissionIds, String adminEmail);

    void resetRolePermissionsToDefault(Role role);

    void resetRolePermissionsToDefault(Role role, String adminEmail);
}
