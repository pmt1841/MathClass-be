package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.dto.DirectChatMessageRequest;
import com.codegym.mathclass.chat.dto.GroupChatMessageRequest;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatStompController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void processMessage(@Payload @Valid ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Unauthorized STOMP message send attempt");
            return;
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((Authentication) principal).getPrincipal();
        Long senderId = userDetails.getId();

        ChatMessageResponse response = chatService.sendMessage(request, senderId);

        // Destination topic 1: /topic/classroom/{classId}/student/{studentId} (Học sinh & Giảng viên trong room)
        String studentTopic = String.format("/topic/classroom/%d/student/%d", request.getClassId(), request.getStudentId());
        messagingTemplate.convertAndSend(studentTopic, response);

        // Destination topic 2: /topic/classroom/{classId}/teacher (Thông báo chung cho Giảng viên nhận tin nhắn tức thời từ mọi học sinh)
        String teacherTopic = String.format("/topic/classroom/%d/teacher", request.getClassId());
        messagingTemplate.convertAndSend(teacherTopic, response);

        log.info("Broadcasted chat message to {} and {}: senderId={}", studentTopic, teacherTopic, senderId);
    }

    @MessageMapping("/chat.sendGroup")
    public void processGroupMessage(@Payload @Valid GroupChatMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Unauthorized STOMP group message send attempt");
            return;
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((Authentication) principal).getPrincipal();
        Long senderId = userDetails.getId();

        ChatMessageResponse response = chatService.sendGroupMessage(request, senderId);

        String groupTopic = String.format("/topic/classroom/%d/group", request.getClassId());
        messagingTemplate.convertAndSend(groupTopic, response);

        log.info("Broadcasted group chat message to {}: senderId={}", groupTopic, senderId);
    }

    @MessageMapping("/chat.sendDirect")
    public void processDirectMessage(@Payload @Valid DirectChatMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Unauthorized STOMP direct message send attempt");
            return;
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((Authentication) principal).getPrincipal();
        Long senderId = userDetails.getId();

        ChatMessageResponse response = chatService.sendDirectMessage(request, senderId);

        // Gửi tới channel nhận của recipient
        String recipientTopic = String.format("/topic/classroom/%d/direct/%d", request.getClassId(), request.getRecipientId());
        messagingTemplate.convertAndSend(recipientTopic, response);

        // Gửi lại tới channel nhận của sender để cập nhật đồng bộ các tab/thiết bị khác của người gửi
        String senderTopic = String.format("/topic/classroom/%d/direct/%d", request.getClassId(), senderId);
        if (!senderTopic.equals(recipientTopic)) {
            messagingTemplate.convertAndSend(senderTopic, response);
        }

        log.info("Broadcasted direct chat message to {} and {}: senderId={}", recipientTopic, senderTopic, senderId);
    }
}
