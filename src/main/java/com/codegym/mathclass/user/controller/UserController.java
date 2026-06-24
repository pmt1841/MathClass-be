package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getCurrentUserProfile(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.codegym.mathclass.security.services.CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getId()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.codegym.mathclass.security.services.CustomUserDetails userDetails,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.codegym.mathclass.user.dto.request.UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userDetails.getId(), request));
    }

    @org.springframework.web.bind.annotation.PostMapping("/avatar")
    public ResponseEntity<java.util.Map<String, String>> uploadAvatar(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.codegym.mathclass.security.services.CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String avatarUrl = userService.uploadAvatar(userDetails.getId(), file);
        return ResponseEntity.ok(java.util.Map.of("avatarUrl", avatarUrl));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }
}
