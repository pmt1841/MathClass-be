package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.user.dto.request.UpdateRolePermissionsRequest;
import com.codegym.mathclass.user.dto.response.PermissionDto;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.RolePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final RolePermissionService rolePermissionService;

    @GetMapping("/{roleName}/permissions")
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<List<PermissionDto>> getRolePermissions(@PathVariable String roleName) {
        Role role = parseRole(roleName);
        return ResponseEntity.ok(rolePermissionService.getPermissionsByRole(role));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<List<PermissionDto>> getAllPermissions() {
        return ResponseEntity.ok(rolePermissionService.getAllPermissions());
    }

    @PutMapping("/{roleName}/permissions")
    @PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable String roleName,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        
        Role role = parseRole(roleName);
        rolePermissionService.updateRolePermissions(role, request.getPermissionIds());
        return ResponseEntity.ok().build();
    }

    private Role parseRole(String roleName) {
        try {
            return Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.codegym.mathclass.exception.BadRequestException("Role không hợp lệ: " + roleName);
        }
    }
}
