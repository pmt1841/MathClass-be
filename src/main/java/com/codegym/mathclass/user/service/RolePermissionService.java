package com.codegym.mathclass.user.service;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.response.PermissionDto;
import com.codegym.mathclass.user.entity.Permission;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.RolePermission;
import com.codegym.mathclass.user.repository.PermissionRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;

    public List<PermissionDto> getPermissionsByRole(Role role) {
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRole(role);
        return rolePermissions.stream()
                .map(rp -> PermissionDto.builder()
                        .id(rp.getPermission().getId())
                        .name(rp.getPermission().getName())
                        .description(rp.getPermission().getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> PermissionDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateRolePermissions(Role role, List<Long> permissionIds) {
        log.info("Updating permissions for role: {}", role);
        
        // Delete all existing permissions for this role
        List<RolePermission> existingRolePermissions = rolePermissionRepository.findByRole(role);
        rolePermissionRepository.deleteAll(existingRolePermissions);

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
                    .collect(Collectors.toList());
            
            rolePermissionRepository.saveAll(newRolePermissions);
        }

        // Evict cache to force reload next time
        permissionCacheService.evictPermissionsCache(role);
        log.info("Successfully updated permissions and evicted cache for role: {}", role);
    }
}
