package com.codegym.mathclass.chat.dto;

import com.codegym.mathclass.chat.entity.ChatType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Builder
public class ChatMessageResponse {
    private Long id;
    private Long classId;
    private Long studentId;
    private Long recipientId;
    private ChatType chatType;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;

    // Constructor 9 tham số cho legacy Student-Teacher chat projection
    public ChatMessageResponse(
            Long id,
            Long classId,
            Long studentId,
            Long senderId,
            String senderName,
            String senderAvatar,
            String content,
            Boolean isRead,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.classId = classId;
        this.studentId = studentId;
        this.recipientId = null;
        this.chatType = ChatType.DIRECT_TEACHER;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.content = content;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    // Constructor 11 tham số cho JPQL Projection (Group & Direct Student chat)
    public ChatMessageResponse(
            Long id,
            Long classId,
            Long studentId,
            Long recipientId,
            ChatType chatType,
            Long senderId,
            String senderName,
            String senderAvatar,
            String content,
            Boolean isRead,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.classId = classId;
        this.studentId = studentId;
        this.recipientId = recipientId;
        this.chatType = chatType;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.content = content;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }
}
