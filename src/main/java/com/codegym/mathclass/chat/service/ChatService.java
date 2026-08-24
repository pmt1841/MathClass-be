package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

public interface ChatService {
    ChatMessageResponse sendMessage(ChatMessageRequest request, Long currentUserId);
    Page<ChatMessageResponse> getChatHistory(String classCode, Long studentId, Long currentUserId, Pageable pageable);
    void markAsRead(String classCode, Long studentId, Long currentUserId);
    Set<Long> getOnlineUsers(String classCode, Long currentUserId);
    List<Long> getUnreadStudentIds(String classCode, Long currentUserId);
}
