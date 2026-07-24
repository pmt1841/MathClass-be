package com.codegym.mathclass.notification.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.notification.dto.NotificationSettingsDto;
import com.codegym.mathclass.notification.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notification Settings", description = "APIs cài đặt bật/tắt nhận thông báo qua Email hoặc In-app")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @Operation(summary = "Lấy cấu hình cài đặt thông báo", description = "Truy vấn các tùy chọn thông báo của người dùng")
    @GetMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> getNotificationSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationSettingsService.getNotificationSettings(userDetails.getId()));
    }

    @Operation(summary = "Cập nhật cài đặt thông báo", description = "Bật hoặc tắt các loại thông báo (Email, In-App)")
    @PutMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> updateNotificationSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody NotificationSettingsDto request) {
        return ResponseEntity.ok(notificationSettingsService.updateNotificationSettings(userDetails.getId(), request));
    }
}
