package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.request.ChatMessageRequest;
import com.codegym.mathclass.chat.dto.request.DirectChatMessageRequest;
import com.codegym.mathclass.chat.dto.request.GroupChatMessageRequest;
import com.codegym.mathclass.chat.dto.response.ChatMessageResponse;
import com.codegym.mathclass.chat.entity.ChatType;
import com.codegym.mathclass.chat.service.ChatService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatStompController chatStompController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
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
        @DisplayName("Should send and broadcast message to student and teacher topics")
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
        }
    }

    @Nested
    @DisplayName("processGroupMessage (Classroom Group Chat) Tests")
    class ProcessGroupMessageTests {

        @Test
        @DisplayName("Should broadcast message to classroom group topic")
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
        }
    }

    @Nested
    @DisplayName("processDirectMessage (1-1 Direct Chat) Tests")
    class ProcessDirectMessageTests {

        @Test
        @DisplayName("Should broadcast message to recipient and sender topics")
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
        }
    }
}
