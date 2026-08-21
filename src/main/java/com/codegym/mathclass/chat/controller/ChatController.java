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

@RestController
@RequestMapping("${mathclass.app.apiPrefix:/api/v1}/classes/{classCode}/chat")
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
}
