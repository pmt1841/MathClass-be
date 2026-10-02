package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.config.DefaultRolePermissions;
import com.codegym.mathclass.user.dto.response.PermissionResponse;
import com.codegym.mathclass.user.entity.Permission;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.RolePermission;
import com.codegym.mathclass.user.repository.PermissionRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import com.codegym.mathclass.user.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;
    private final SystemLogService systemLogService;

    @Override
    public List<PermissionResponse> getPermissionsByRole(Role role) {
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleWithPermission(role);
        return rolePermissions.stream()
                .map(rp -> PermissionResponse.builder()
                        .id(rp.getPermission().getId())
                        .name(rp.getPermission().getName())
                        .description(rp.getPermission().getDescription())
                        .build())
                .toList();
    }

    @Override
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void updateRolePermissions(Role role, List<Long> permissionIds) {
        updateRolePermissions(role, permissionIds, null);
    }

    @Override
    @Transactional
    public void updateRolePermissions(Role role, List<Long> permissionIds, String adminEmail) {
        log.info("Updating permissions for role: {}", role);

        // Delete all existing permissions for this role
        List<RolePermission> existingRolePermissions = rolePermissionRepository.findByRole(role);
        rolePermissionRepository.deleteAll(existingRolePermissions);
        rolePermissionRepository.flush();

        // Save new permissions
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(permissionIds);

            if (permissions.size() != permissionIds.size()) {
                throw new BadRequestException("Lỗi dữ liệu: Có quyền truy cập không tồn tại.");
            }

            List<RolePermission> newRolePermissions = permissions.stream()
                    .map(p -> RolePermission.builder()
                            .role(role)
                            .permission(p)
                            .build())
                    .toList();

            rolePermissionRepository.saveAll(newRolePermissions);
        }

        // Evict all role permission caches immediately
        permissionCacheService.evictAllPermissionsCache();
        log.info("Successfully updated permissions for role: {}", role);

        if (adminEmail != null && !adminEmail.isBlank()) {
            systemLogService.logWarning(adminEmail, "Cập nhật danh sách phân quyền cho nhóm " + role.name(), null);
        }
    }

    @Override
    @Transactional
    public void resetRolePermissionsToDefault(Role role) {
        resetRolePermissionsToDefault(role, null);
    }

    @Override
    @Transactional
    public void resetRolePermissionsToDefault(Role role, String adminEmail) {
        log.info("Resetting permissions to default for role: {}", role);

        List<String> defaultPermissionNames = DefaultRolePermissions.getDefaultPermissions(role);

        List<RolePermission> existingRolePermissions = rolePermissionRepository.findByRole(role);
        rolePermissionRepository.deleteAll(existingRolePermissions);
        rolePermissionRepository.flush();

        if (defaultPermissionNames != null && !defaultPermissionNames.isEmpty()) {
            List<Permission> defaultPermissions = permissionRepository.findByNameIn(defaultPermissionNames);

            List<RolePermission> newRolePermissions = defaultPermissions.stream()
                    .map(p -> RolePermission.builder()
                            .role(role)
                            .permission(p)
                            .build())
                    .toList();

            rolePermissionRepository.saveAll(newRolePermissions);
        }

        permissionCacheService.evictAllPermissionsCache();
        log.info("Successfully reset permissions to default for role: {}", role);

        if (adminEmail != null && !adminEmail.isBlank()) {
            systemLogService.logWarning(adminEmail, "Khôi phục cài đặt phân quyền mặc định cho nhóm " + role.name(),
                    null);
        }
    }
}
