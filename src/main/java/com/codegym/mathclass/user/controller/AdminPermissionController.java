package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.user.dto.request.UpdateRolePermissionsRequest;
import com.codegym.mathclass.user.dto.response.PermissionDto;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.RolePermissionService;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - Role Permissions", description = "APIs quản trị viên: Xem và gán quyền (Permissions) theo vai trò (Roles)")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
public class AdminPermissionController {

    private final RolePermissionService rolePermissionService;
    private final SystemLogService systemLogService;

    @Operation(summary = "Lấy tất cả các Quyền hệ thống", description = "Danh sách tất cả các permission có sẵn")
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionDto>> getAllPermissions() {
        return ResponseEntity.ok(rolePermissionService.getAllPermissions());
    }

    @Operation(summary = "Lấy danh sách Quyền theo Vai trò", description = "Truy vấn các quyền được gán cho vai trò cụ thể (VD: ADMIN, TEACHER, STUDENT)")
    @GetMapping("/{roleName}/permissions")
    public ResponseEntity<List<PermissionDto>> getPermissionsByRole(@PathVariable String roleName) {
        Role role = parseRole(roleName);
        return ResponseEntity.ok(rolePermissionService.getPermissionsByRole(role));
    }

    @Operation(summary = "Cập nhật Quyền cho Vai trò", description = "Gán lại danh sách quyền (Permission IDs) cho một vai trò")
    @PutMapping("/{roleName}/permissions")
    public ResponseEntity<Map<String, String>> updateRolePermissions(
            @PathVariable String roleName,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Role role = parseRole(roleName);
        rolePermissionService.updateRolePermissions(role, request.getPermissionIds());
        
        systemLogService.logWarning(userDetails.getUsername(), 
            "Cập nhật danh sách phân quyền cho nhóm " + role.name(), null);
            
        return ResponseEntity.ok(Map.of("message", "Cập nhật phân quyền thành công."));
    }

    @Operation(summary = "Khôi phục Quyền mặc định cho Vai trò", description = "Đặt lại danh sách quyền của vai trò về cài đặt mặc định ban đầu")
    @PostMapping("/{roleName}/reset-permissions")
    public ResponseEntity<Map<String, String>> resetRolePermissions(
            @PathVariable String roleName,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Role role = parseRole(roleName);
        rolePermissionService.resetRolePermissionsToDefault(role);
        
        systemLogService.logWarning(userDetails.getUsername(), 
            "Khôi phục cài đặt phân quyền mặc định cho nhóm " + role.name(), null);
            
        return ResponseEntity.ok(Map.of("message", "Khôi phục phân quyền mặc định thành công."));
    }

    private Role parseRole(String roleName) {
        try {
            return Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role không hợp lệ: " + roleName);
        }
    }
}
