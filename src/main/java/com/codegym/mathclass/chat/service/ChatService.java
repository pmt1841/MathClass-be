package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import com.codegym.mathclass.chat.dto.DirectChatMessageRequest;
import com.codegym.mathclass.chat.dto.GroupChatMessageRequest;

public interface ChatService {
    ChatMessageResponse sendMessage(ChatMessageRequest request, Long currentUserId);
    Page<ChatMessageResponse> getChatHistory(String classCode, Long studentId, Long currentUserId, Pageable pageable);
    void markAsRead(String classCode, Long studentId, Long currentUserId);
    Set<Long> getOnlineUsers(String classCode, Long currentUserId);
    List<Long> getUnreadStudentIds(String classCode, Long currentUserId);

    // Chat Nhóm Lớp & Chat Riêng 1-1 giữa Học sinh
    ChatMessageResponse sendGroupMessage(GroupChatMessageRequest request, Long currentUserId);
    ChatMessageResponse sendDirectMessage(DirectChatMessageRequest request, Long currentUserId);
    Page<ChatMessageResponse> getGroupChatHistory(String classCode, Long currentUserId, Pageable pageable);
    Page<ChatMessageResponse> getDirectChatHistory(String classCode, Long otherUserId, Long currentUserId, Pageable pageable);
    void markDirectAsRead(String classCode, Long otherUserId, Long currentUserId);
}
