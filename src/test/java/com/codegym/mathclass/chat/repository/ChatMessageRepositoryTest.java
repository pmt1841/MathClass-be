package com.codegym.mathclass.chat.repository;

import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.entity.ChatMessage;
import com.codegym.mathclass.chat.entity.ChatType;
import com.codegym.mathclass.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageRepositoryTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("Test query findHistoryByClassIdAndStudentId với JPQL projection DTO và phân trang")
    void testFindHistoryByClassIdAndStudentId_pagingAndProjection() {
        ChatMessageResponse dto = new ChatMessageResponse(
                1L, 100L, 20L, 20L, "Lê Thị B", null, "Bài toán phân trang", false, LocalDateTime.now()
        );
        Page<ChatMessageResponse> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(chatMessageRepository.findHistoryByClassIdAndStudentId(eq(100L), eq(20L), any(PageRequest.class)))
                .thenReturn(page);

        Page<ChatMessageResponse> result = chatMessageRepository.findHistoryByClassIdAndStudentId(100L, 20L, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Bài toán phân trang", result.getContent().get(0).getContent());
    }

    @Test
    @DisplayName("Test query findGroupHistoryByClassId trả về phân trang tin nhắn nhóm lớp")
    void testFindGroupHistoryByClassId() {
        User sender = new User();
        sender.setId(20L);
        sender.setFullName("Học sinh A");

        ChatMessage message = ChatMessage.builder()
                .classId(100L)
                .chatType(ChatType.CLASS_GROUP)
                .sender(sender)
                .content("Tin nhắn nhóm lớp")
                .isRead(false)
                .build();
        message.setId(5L);

        Page<ChatMessage> page = new PageImpl<>(List.of(message), PageRequest.of(0, 20), 1);
        when(chatMessageRepository.findGroupHistoryByClassId(eq(100L), any(PageRequest.class))).thenReturn(page);

        Page<ChatMessage> result = chatMessageRepository.findGroupHistoryByClassId(100L, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(ChatType.CLASS_GROUP, result.getContent().get(0).getChatType());
    }

    @Test
    @DisplayName("Test query findDirectHistoryByClassIdAndUsers giữa 2 người dùng")
    void testFindDirectHistoryByClassIdAndUsers() {
        User userA = new User();
        userA.setId(20L);

        ChatMessage message = ChatMessage.builder()
                .classId(100L)
                .recipientId(30L)
                .chatType(ChatType.DIRECT_STUDENT)
                .sender(userA)
                .content("Tin nhắn 1-1")
                .isRead(false)
                .build();
        message.setId(8L);

        Page<ChatMessage> page = new PageImpl<>(List.of(message), PageRequest.of(0, 20), 1);
        when(chatMessageRepository.findDirectHistoryByClassIdAndUsers(eq(100L), eq(20L), eq(30L), any(PageRequest.class)))
                .thenReturn(page);

        Page<ChatMessage> result = chatMessageRepository.findDirectHistoryByClassIdAndUsers(100L, 20L, 30L, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(30L, result.getContent().get(0).getRecipientId());
    }

    @Test
    @DisplayName("Test countUnreadGroupMessages đếm số tin nhắn nhóm chưa đọc theo mốc thời gian")
    void testCountUnreadGroupMessages() {
        LocalDateTime lastReadAt = LocalDateTime.now().minusHours(1);
        when(chatMessageRepository.countUnreadGroupMessages(100L, 20L, lastReadAt)).thenReturn(3L);

        long count = chatMessageRepository.countUnreadGroupMessages(100L, 20L, lastReadAt);

        assertEquals(3L, count);
    }
}
