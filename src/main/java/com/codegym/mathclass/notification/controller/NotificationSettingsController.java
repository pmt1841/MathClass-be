package com.codegym.mathclass.notification.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.notification.dto.NotificationSettingsDto;
import com.codegym.mathclass.notification.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> getNotificationSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationSettingsService.getNotificationSettings(userDetails.getId()));
    }

    @PutMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> updateNotificationSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody NotificationSettingsDto request) {
        return ResponseEntity.ok(notificationSettingsService.updateNotificationSettings(userDetails.getId(), request));
    }
}
