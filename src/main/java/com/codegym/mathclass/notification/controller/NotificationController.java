package com.codegym.mathclass.notification.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.notification.dto.NotificationResponse;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.codegym.mathclass.exception.AccessDeniedException;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notifications", description = "APIs thông báo thời gian thực SSE, danh sách thông báo và đánh dấu đã đọc")
@RestController
@ApiVersion(1)
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Đăng ký SSE Stream thông báo thời gian thực", description = "Kết nối Server-Sent Events (SSE) để nhận thông báo real-time từ hệ thống")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        return notificationService.createEmitter(userDetails.getId());
    }

    @Operation(summary = "Danh sách thông báo cá nhân", description = "Lấy danh sách các thông báo của người dùng hiện tại có phân trang")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getNotifications(userDetails.getId(), PageRequest.of(page, size)));
    }

    @Operation(summary = "Số lượng thông báo chưa đọc", description = "Lấy số lượng thông báo chưa đọc của người dùng")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = notificationService.getUnreadCount(userDetails.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc", description = "Chuyển tất cả thông báo của người dùng sang trạng thái đã đọc")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Đánh dấu một thông báo là đã đọc", description = "Chuyển trạng thái của thông báo theo ID sang đã đọc")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAsRead(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }
}
