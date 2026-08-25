package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.common.dto.ApiResponse;
import com.codegym.mathclass.security.services.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/classrooms/{classCode}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getChatHistory(
            @PathVariable String classCode,
            @RequestParam(required = false) Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ChatMessageResponse> history = chatService.getChatHistory(classCode, studentId, userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ChatMessageResponse>>builder()
                .message("Lấy lịch sử tin nhắn thành công")
                .result(history)
                .build());
    }

    @PutMapping("/messages/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String classCode,
            @RequestParam(required = false) Long studentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        chatService.markAsRead(classCode, studentId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Đã đánh dấu các tin nhắn là đã đọc")
                .build());
    }

    @GetMapping("/online-users")
    public ResponseEntity<ApiResponse<Set<Long>>> getOnlineUsers(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Set<Long> onlineUserIds = chatService.getOnlineUsers(classCode, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.<Set<Long>>builder()
                .message("Lấy danh sách người dùng online thành công")
                .result(onlineUserIds)
                .build());
    }

    @GetMapping("/unread-students")
    public ResponseEntity<ApiResponse<List<Long>>> getUnreadStudentIds(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<Long> unreadStudentIds = chatService.getUnreadStudentIds(classCode, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.<List<Long>>builder()
                .message("Lấy danh sách học sinh có tin nhắn chưa đọc thành công")
                .result(unreadStudentIds)
                .build());
    }

    @GetMapping("/group/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getGroupChatHistory(
            @PathVariable String classCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ChatMessageResponse> history = chatService.getGroupChatHistory(classCode, userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ChatMessageResponse>>builder()
                .message("Lấy lịch sử chat nhóm thành công")
                .result(history)
                .build());
    }

    @GetMapping("/direct/{otherUserId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getDirectChatHistory(
            @PathVariable String classCode,
            @PathVariable Long otherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ChatMessageResponse> history = chatService.getDirectChatHistory(classCode, otherUserId, userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ChatMessageResponse>>builder()
                .message("Lấy lịch sử chat riêng 1-1 thành công")
                .result(history)
                .build());
    }

    @PutMapping("/direct/{otherUserId}/read")
    public ResponseEntity<ApiResponse<Void>> markDirectAsRead(
            @PathVariable String classCode,
            @PathVariable Long otherUserId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        chatService.markDirectAsRead(classCode, otherUserId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Đã đánh dấu các tin nhắn riêng là đã đọc")
                .build());
    }
}
