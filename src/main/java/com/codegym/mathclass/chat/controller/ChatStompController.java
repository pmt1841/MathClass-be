package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.ChatMessageResponse;
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

        // Destination topic broadcast: /topic/classroom/{classId}/student/{studentId}
        String destination = String.format("/topic/classroom/%d/student/%d", request.getClassId(), request.getStudentId());

        messagingTemplate.convertAndSend(destination, response);
        log.info("Broadcasted chat message to destination {}: senderId={}", destination, senderId);
    }
}
