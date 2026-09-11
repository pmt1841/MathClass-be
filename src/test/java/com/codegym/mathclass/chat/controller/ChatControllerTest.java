package com.codegym.mathclass.chat.controller;

import com.codegym.mathclass.chat.dto.response.ChatMessageResponse;
import com.codegym.mathclass.chat.dto.response.ClassroomChatUnreadSummaryResponse;
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
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                1L, "Teacher", "teacher@mathclass.edu.vn", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(chatController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockUserDetails;
                    }
                })
                .build();
    }

    private ChatMessageResponse buildMessageResponse(Long id, String content) {
        return ChatMessageResponse.builder()
                .id(id)
                .classId(10L)
                .studentId(2L)
                .chatType(ChatType.DIRECT_TEACHER)
                .senderId(1L)
                .senderName("Teacher")
                .content(content)
                .isRead(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Teacher-Student Chat Endpoints")
    class TeacherStudentChatTests {

        @Test
        @DisplayName("GET /classrooms/{classCode}/chat/messages - Should get message history")
        void getChatHistory_Success() throws Exception {
            ChatMessageResponse msg = buildMessageResponse(100L, "Xin chào em!");
            Page<ChatMessageResponse> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1);

            when(chatService.getChatHistory(eq("MATH101"), eq(2L), eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/classrooms/MATH101/chat/messages")
                            .param("studentId", "2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content[0].id").value(100L))
                    .andExpect(jsonPath("$.result.content[0].content").value("Xin chào em!"));
        }

        @Test
        @DisplayName("PUT /classrooms/{classCode}/chat/messages/read - Should mark messages as read")
        void markAsRead_Success() throws Exception {
            doNothing().when(chatService).markAsRead("MATH101", 2L, 1L);

            mockMvc.perform(put("/classrooms/MATH101/chat/messages/read")
                            .param("studentId", "2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đã đánh dấu các tin nhắn là đã đọc"));

            verify(chatService).markAsRead("MATH101", 2L, 1L);
        }
    }

    @Nested
    @DisplayName("Chat Metadata Endpoints")
    class ChatMetadataTests {

        @Test
        @DisplayName("GET /classrooms/{classCode}/chat/online-users - Should return online user IDs")
        void getOnlineUsers_Success() throws Exception {
            when(chatService.getOnlineUsers("MATH101", 1L)).thenReturn(Set.of(1L, 2L, 3L));

            mockMvc.perform(get("/classrooms/MATH101/chat/online-users")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.length()").value(3));
        }

        @Test
        @DisplayName("GET /classrooms/{classCode}/chat/unread-students - Should return unread student IDs")
        void getUnreadStudentIds_Success() throws Exception {
            when(chatService.getUnreadStudentIds("MATH101", 1L)).thenReturn(List.of(2L, 5L));

            mockMvc.perform(get("/classrooms/MATH101/chat/unread-students")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result[0]").value(2L))
                    .andExpect(jsonPath("$.result[1]").value(5L));
        }

        @Test
        @DisplayName("GET /classrooms/{classCode}/chat/unread-summary - Should return unread summary")
        void getUnreadSummary_Success() throws Exception {
            ClassroomChatUnreadSummaryResponse summary = ClassroomChatUnreadSummaryResponse.builder()
                    .hasGroupUnread(true)
                    .groupUnreadCount(3L)
                    .unreadStudentIds(List.of(2L))
                    .studentUnreadCounts(Map.of(2L, 1L))
                    .build();

            when(chatService.getUnreadSummary("MATH101", 1L)).thenReturn(summary);

            mockMvc.perform(get("/classrooms/MATH101/chat/unread-summary")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.hasGroupUnread").value(true))
                    .andExpect(jsonPath("$.result.groupUnreadCount").value(3));
        }
    }

    @Nested
    @DisplayName("Group Chat Endpoints")
    class GroupChatTests {

        @Test
        @DisplayName("GET /classrooms/{classCode}/chat/group/messages - Should return group chat messages")
        void getGroupChatHistory_Success() throws Exception {
            ChatMessageResponse msg = buildMessageResponse(200L, "Thông báo chung cho cả lớp");
            Page<ChatMessageResponse> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1);

            when(chatService.getGroupChatHistory(eq("MATH101"), eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/classrooms/MATH101/chat/group/messages")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content[0].id").value(200L))
                    .andExpect(jsonPath("$.result.content[0].content").value("Thông báo chung cho cả lớp"));
        }

        @Test
        @DisplayName("PUT /classrooms/{classCode}/chat/group/read - Should mark group messages as read")
        void markGroupAsRead_Success() throws Exception {
            doNothing().when(chatService).markGroupAsRead("MATH101", 1L);

            mockMvc.perform(put("/classrooms/MATH101/chat/group/read")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đã đánh dấu các tin nhắn nhóm là đã đọc"));

            verify(chatService).markGroupAsRead("MATH101", 1L);
        }
    }

    @Nested
    @DisplayName("Direct 1-1 Chat Endpoints")
    class DirectChatTests {

        @Test
        @DisplayName("GET /classrooms/{classCode}/chat/direct/{otherUserId}/messages - Should return direct messages")
        void getDirectChatHistory_Success() throws Exception {
            ChatMessageResponse msg = buildMessageResponse(300L, "Tin nhắn riêng 1-1");
            Page<ChatMessageResponse> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1);

            when(chatService.getDirectChatHistory(eq("MATH101"), eq(5L), eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/classrooms/MATH101/chat/direct/5/messages")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content[0].id").value(300L))
                    .andExpect(jsonPath("$.result.content[0].content").value("Tin nhắn riêng 1-1"));
        }

        @Test
        @DisplayName("PUT /classrooms/{classCode}/chat/direct/{otherUserId}/read - Should mark direct messages as read")
        void markDirectAsRead_Success() throws Exception {
            doNothing().when(chatService).markDirectAsRead("MATH101", 5L, 1L);

            mockMvc.perform(put("/classrooms/MATH101/chat/direct/5/read")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đã đánh dấu các tin nhắn riêng là đã đọc"));

            verify(chatService).markDirectAsRead("MATH101", 5L, 1L);
        }
    }
}
