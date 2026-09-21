package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.event.ChatBroadcastEvent;
import com.codegym.mathclass.chat.dto.request.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.request.DirectChatMessageRequest;
import com.codegym.mathclass.chat.dto.request.GroupChatMessageRequest;
import com.codegym.mathclass.chat.dto.response.ChatMessageResponse;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ChatStompControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RTopic chatTopic;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatStompController chatStompController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getTopic(ChatStompController.CHAT_TOPIC_NAME)).thenReturn(chatTopic);
        lenient().when(redissonClient.getTopic(org.mockito.ArgumentMatchers.eq(ChatStompController.CHAT_TOPIC_NAME), org.mockito.ArgumentMatchers.any())).thenReturn(chatTopic);

        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@mathclass.edu.vn", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
    }

    private ChatMessageResponse buildResponse(Long id, Long classId, String content) {
        return ChatMessageResponse.builder()
                .id(id)
                .classId(classId)
                .content(content)
                .senderId(1L)
                .senderName("Teacher")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("processMessage (Student-Teacher Chat) Tests")
    class ProcessMessageTests {

        @Test
        @DisplayName("Should send and broadcast message to student and teacher topics locally and publish to Redis cluster")
        void processMessage_Success() {
            ChatMessageRequest request = ChatMessageRequest.builder()
                    .classId(10L)
                    .studentId(2L)
                    .content("Em chú ý hoàn thành bài tập sớm nhé")
                    .build();

            ChatMessageResponse response = buildResponse(100L, 10L, request.getContent());

            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(chatService.sendMessage(request, 1L)).thenReturn(response);

            chatStompController.processMessage(request, authentication);

            verify(chatService).sendMessage(request, 1L);
            verify(messagingTemplate).convertAndSend("/topic/classroom/10/student/2", response);
            verify(messagingTemplate).convertAndSend("/topic/classroom/10/teacher", response);

            // Verify published to Redis cluster
            verify(chatTopic, times(2)).publish(any(ChatBroadcastEvent.class));
        }

        @Test
        @DisplayName("Should ignore message when principal is null")
        void processMessage_NullPrincipal_Ignored() {
            ChatMessageRequest request = ChatMessageRequest.builder()
                    .classId(10L)
                    .studentId(2L)
                    .content("Unauthorized message")
                    .build();

            chatStompController.processMessage(request, null);

            verifyNoInteractions(chatService);
            verifyNoInteractions(messagingTemplate);
            verifyNoInteractions(chatTopic);
        }
    }

    @Nested
    @DisplayName("processGroupMessage (Classroom Group Chat) Tests")
    class ProcessGroupMessageTests {

        @Test
        @DisplayName("Should broadcast message to classroom group topic locally and publish to Redis cluster")
        void processGroupMessage_Success() {
            GroupChatMessageRequest request = GroupChatMessageRequest.builder()
                    .classId(10L)
                    .content("Hôm nay lớp nghỉ sớm 15 phút nhé")
                    .build();

            ChatMessageResponse response = buildResponse(101L, 10L, request.getContent());

            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(chatService.sendGroupMessage(request, 1L)).thenReturn(response);

            chatStompController.processGroupMessage(request, authentication);

            verify(chatService).sendGroupMessage(request, 1L);
            verify(messagingTemplate).convertAndSend("/topic/classroom/10/group", response);
            verify(chatTopic, times(1)).publish(any(ChatBroadcastEvent.class));
        }

        @Test
        @DisplayName("Should ignore group message when principal is null")
        void processGroupMessage_NullPrincipal_Ignored() {
            GroupChatMessageRequest request = GroupChatMessageRequest.builder()
                    .classId(10L)
                    .content("Unauthorized group message")
                    .build();

            chatStompController.processGroupMessage(request, null);

            verifyNoInteractions(chatService);
            verifyNoInteractions(messagingTemplate);
            verifyNoInteractions(chatTopic);
        }
    }

    @Nested
    @DisplayName("processDirectMessage (1-1 Direct Chat) Tests")
    class ProcessDirectMessageTests {

        @Test
        @DisplayName("Should broadcast message to recipient and sender topics locally and publish to Redis cluster")
        void processDirectMessage_Success() {
            DirectChatMessageRequest request = DirectChatMessageRequest.builder()
                    .classId(10L)
                    .recipientId(5L)
                    .content("Chào em, có thắc mắc gì về bài kiểm tra không?")
                    .build();

            ChatMessageResponse response = buildResponse(102L, 10L, request.getContent());

            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(chatService.sendDirectMessage(request, 1L)).thenReturn(response);

            chatStompController.processDirectMessage(request, authentication);

            verify(chatService).sendDirectMessage(request, 1L);
            verify(messagingTemplate).convertAndSend("/topic/classroom/10/direct/5", response);
            verify(messagingTemplate).convertAndSend("/topic/classroom/10/direct/1", response);
            verify(chatTopic, times(2)).publish(any(ChatBroadcastEvent.class));
        }

        @Test
        @DisplayName("Should ignore direct message when principal is null")
        void processDirectMessage_NullPrincipal_Ignored() {
            DirectChatMessageRequest request = DirectChatMessageRequest.builder()
                    .classId(10L)
                    .recipientId(5L)
                    .content("Unauthorized direct message")
                    .build();

            chatStompController.processDirectMessage(request, null);

            verifyNoInteractions(chatService);
            verifyNoInteractions(messagingTemplate);
            verifyNoInteractions(chatTopic);
        }
    }

    @Nested
    @DisplayName("Redis Cluster Subscriber Tests")
    class ClusterSubscriberTests {

        @Test
        @DisplayName("Should forward remote chat message to local STOMP subscribers")
        void subscriber_ForwardRemoteMessage() {
            ArgumentCaptor<MessageListener<ChatBroadcastEvent>> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
            when(chatTopic.addListener(eq(ChatBroadcastEvent.class), listenerCaptor.capture())).thenReturn(1);

            chatStompController.initSubscriber();

            MessageListener<ChatBroadcastEvent> listener = listenerCaptor.getValue();
            assertThat(listener).isNotNull();

            ChatMessageResponse response = buildResponse(103L, 10L, "Tin nhắn từ node khác");
            ChatBroadcastEvent event = new ChatBroadcastEvent("other-instance-id", "/topic/classroom/10/group", response);

            listener.onMessage("chat:events", event);

            verify(messagingTemplate).convertAndSend("/topic/classroom/10/group", response);
        }

        @Test
        @DisplayName("Should ignore chat message originated from current instance to avoid double send")
        void subscriber_IgnoreSelfOriginatedMessage() {
            ArgumentCaptor<MessageListener<ChatBroadcastEvent>> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
            when(chatTopic.addListener(eq(ChatBroadcastEvent.class), listenerCaptor.capture())).thenReturn(1);

            chatStompController.initSubscriber();

            MessageListener<ChatBroadcastEvent> listener = listenerCaptor.getValue();
            assertThat(listener).isNotNull();

            ChatMessageResponse response = buildResponse(104L, 10L, "Tin nhắn từ chính node này");
            ChatBroadcastEvent event = new ChatBroadcastEvent(chatStompController.getInstanceId(), "/topic/classroom/10/group", response);

            listener.onMessage("chat:events", event);

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }
    }
}
