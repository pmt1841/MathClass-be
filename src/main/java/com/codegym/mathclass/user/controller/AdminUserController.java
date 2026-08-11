package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.user.dto.request.UpdateUserStatusRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - Users", description = "APIs quản trị viên: Tìm kiếm, phân trang và quản lý trạng thái tài khoản")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Danh sách tài khoản (Quản trị)", description = "Lấy danh sách tài khoản người dùng theo vai trò, trạng thái kích hoạt và từ khóa tìm kiếm")
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {
        
        Page<UserResponse> users = adminUserService.getUsersForAdmin(role, isActive, search, pageable);
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Khóa / Mở khóa tài khoản", description = "Cập nhật trạng thái isActive (hoạt động / bị khóa) của người dùng")
    @PatchMapping("/{id}/status")
    public ResponseEntity<MessageResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        adminUserService.updateUserStatus(id, request, userDetails.getUsername());
        return ResponseEntity.ok(new MessageResponse("Trạng thái tài khoản đã được cập nhật thành công."));
    }
}

