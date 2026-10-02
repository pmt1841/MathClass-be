package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.request.UpdateRolePermissionsRequest;
import com.codegym.mathclass.user.dto.response.PermissionResponse;
import com.codegym.mathclass.user.dto.response.UserMessageResponse;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Role Permissions", description = "APIs quản trị viên: Xem và gán quyền (Permissions) theo vai trò (Roles)")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('user:manage') or hasRole('ADMIN')")
public class AdminPermissionController {

    private final RolePermissionService rolePermissionService;

    @Operation(summary = "Lấy tất cả các Quyền hệ thống", description = "Danh sách tất cả các permission có sẵn")
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        return ResponseEntity.ok(rolePermissionService.getAllPermissions());
    }

    @Operation(summary = "Lấy danh sách Quyền theo Vai trò", description = "Truy vấn các quyền được gán cho vai trò cụ thể (VD: ADMIN, TEACHER, STUDENT)")
    @GetMapping("/{roleName}/permissions")
    public ResponseEntity<List<PermissionResponse>> getPermissionsByRole(@PathVariable String roleName) {
        Role role = parseRole(roleName);
        return ResponseEntity.ok(rolePermissionService.getPermissionsByRole(role));
    }

    @Operation(summary = "Cập nhật Quyền cho Vai trò", description = "Gán lại danh sách quyền (Permission IDs) cho một vai trò")
    @PutMapping("/{roleName}/permissions")
    public ResponseEntity<UserMessageResponse> updateRolePermissions(
            @PathVariable String roleName,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Role role = parseRole(roleName);
        rolePermissionService.updateRolePermissions(role, request.getPermissionIds(), userDetails.getUsername());
            
        return ResponseEntity.ok(new UserMessageResponse("Cập nhật phân quyền thành công."));
    }

    @Operation(summary = "Khôi phục Quyền mặc định cho Vai trò", description = "Đặt lại danh sách quyền của vai trò về cài đặt mặc định ban đầu")
    @PostMapping("/{roleName}/reset-permissions")
    public ResponseEntity<UserMessageResponse> resetRolePermissions(
            @PathVariable String roleName,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Role role = parseRole(roleName);
        rolePermissionService.resetRolePermissionsToDefault(role, userDetails.getUsername());
            
        return ResponseEntity.ok(new UserMessageResponse("Khôi phục phân quyền mặc định thành công."));
    }

    private Role parseRole(String roleName) {
        try {
            return Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role không hợp lệ: " + roleName);
        }
    }
}
