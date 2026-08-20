package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.common.dto.ApiResponse;

@Tag(name = "User Profile", description = "APIs quản lý thông tin cá nhân và ảnh đại diện của người dùng")
@RestController
@ApiVersion(1)
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Lấy thông tin tài khoản hiện tại", description = "Trả về thông tin chi tiết của người dùng đang đăng nhập")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getId()));
    }

    @Operation(summary = "Cập nhật thông tin cá nhân", description = "Cập nhật họ tên, thông tin bổ sung của người dùng")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userDetails.getId(), request));
    }

    @Operation(summary = "Tải lên ảnh đại diện (Avatar)", description = "Upload file ảnh đại diện và lưu đường dẫn ảnh")
    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = userService.uploadAvatar(userDetails.getId(), file);
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    @Operation(summary = "Đổi mật khẩu tài khoản cá nhân", description = "Thay đổi mật khẩu đăng nhập của người dùng đang đăng nhập")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.")
                .build());
    }

}
