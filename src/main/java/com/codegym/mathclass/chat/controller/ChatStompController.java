package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.event.ChatBroadcastEvent;
import com.codegym.mathclass.chat.dto.request.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.request.DirectChatMessageRequest;
import com.codegym.mathclass.chat.dto.request.GroupChatMessageRequest;
import com.codegym.mathclass.chat.dto.response.ChatMessageResponse;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatStompController {

    public static final String CHAT_TOPIC_NAME = "chat:events";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedissonClient redissonClient;
    private final String instanceId = UUID.randomUUID().toString();
    private int chatListenerId = -1;

    @PostConstruct
    public void initSubscriber() {
        try {
            RTopic topic = redissonClient.getTopic(CHAT_TOPIC_NAME);
            chatListenerId = topic.addListener(ChatBroadcastEvent.class, (channel, event) -> {
                if (event != null && !instanceId.equals(event.originInstanceId())) {
                    log.debug("Received chat event from cluster: topic={}", event.destinationTopic());
                    messagingTemplate.convertAndSend(event.destinationTopic(), event.message());
                }
            });
            log.info("Initialized Redis chat topic listener on '{}' with instanceId={}", CHAT_TOPIC_NAME, instanceId);
        } catch (Exception ex) {
            log.warn("Failed to register Redis chat topic listener: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (chatListenerId != -1) {
            try {
                redissonClient.getTopic(CHAT_TOPIC_NAME).removeListener(chatListenerId);
                log.info("Removed Redis chat topic listener for instanceId={}", instanceId);
            } catch (Exception ex) {
                log.debug("Error removing chat listener on shutdown: {}", ex.getMessage());
            }
        }
    }

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
        broadcastChatMessage(studentTopic, response);

        // Destination topic 2: /topic/classroom/{classId}/teacher (Thông báo chung cho Giảng viên nhận tin nhắn tức thời từ mọi học sinh)
        String teacherTopic = String.format("/topic/classroom/%d/teacher", request.getClassId());
        broadcastChatMessage(teacherTopic, response);

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
        broadcastChatMessage(groupTopic, response);

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
        broadcastChatMessage(recipientTopic, response);

        // Gửi lại tới channel nhận của sender để cập nhật đồng bộ các tab/thiết bị khác của người gửi
        String senderTopic = String.format("/topic/classroom/%d/direct/%d", request.getClassId(), senderId);
        if (!senderTopic.equals(recipientTopic)) {
            broadcastChatMessage(senderTopic, response);
        }

        log.info("Broadcasted direct chat message to {} and {}: senderId={}", recipientTopic, senderTopic, senderId);
    }

    public void broadcastChatMessage(String destinationTopic, ChatMessageResponse response) {
        // 1. Gửi cục bộ cho client kết nối trực tiếp với node hiện tại
        messagingTemplate.convertAndSend(destinationTopic, response);

        // 2. Publish lên Redis cluster để các node khác nhận và gửi cho client của họ
        try {
            RTopic topic = redissonClient.getTopic(CHAT_TOPIC_NAME);
            topic.publish(new ChatBroadcastEvent(instanceId, destinationTopic, response));
        } catch (Exception ex) {
            log.warn("Failed to publish chat event to Redis cluster for topic {}: {}", destinationTopic, ex.getMessage());
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
