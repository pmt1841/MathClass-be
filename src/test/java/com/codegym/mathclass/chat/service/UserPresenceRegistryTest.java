package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPresenceRegistryTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    private UserPresenceRegistry registry;

    private final Long userId = 100L;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        registry = new UserPresenceRegistry(messagingTemplate, userRepository);
        userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getId()).thenReturn(userId);
    }

    private SessionConnectedEvent createConnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userDetails, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionConnectedEvent(this, message);
    }

    private SessionDisconnectEvent createDisconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userDetails, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("Single tab: Connect -> isOnline is true, update DB and broadcast online")
    void connectSingleTab_UserBecomesOnline() {
        SessionConnectedEvent connectEvent = createConnectEvent("session-1");

        registry.handleWebSocketConnectListener(connectEvent);

        assertThat(registry.isUserOnline(userId)).isTrue();
        assertThat(registry.getOnlineUserIds()).contains(userId);

        verify(userRepository).updateLastActiveAt(eq(userId), any(LocalDateTime.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), payloadCaptor.capture());

        Map<?, ?> payload = (Map<?, ?>) payloadCaptor.getValue();
        assertThat(payload.get("userId")).isEqualTo(userId);
        assertThat(payload.get("isOnline")).isEqualTo(true);
    }

    @Test
    @DisplayName("Multiple tabs: Connect tab 1, then tab 2 -> user stays online")
    void connectMultipleTabs_UserStaysOnline() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        registry.handleWebSocketConnectListener(createConnectEvent("session-2"));

        assertThat(registry.isUserOnline(userId)).isTrue();
        verify(userRepository, times(2)).updateLastActiveAt(eq(userId), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Multiple tabs: Disconnect 1 of 2 tabs -> user stays online and does NOT broadcast offline")
    void disconnectOneOfMultipleTabs_UserStaysOnline() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        registry.handleWebSocketConnectListener(createConnectEvent("session-2"));

        clearInvocations(messagingTemplate);
        clearInvocations(userRepository);

        registry.handleWebSocketDisconnectListener(createDisconnectEvent("session-1"));

        // User is still online with session-2
        assertThat(registry.isUserOnline(userId)).isTrue();
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(userRepository, never()).updateLastActiveAt(any(), any());
    }

    @Test
    @DisplayName("Close all tabs: Disconnect last tab -> user goes offline, updates DB and broadcasts offline")
    void disconnectAllTabs_UserBecomesOffline() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        registry.handleWebSocketConnectListener(createConnectEvent("session-2"));

        registry.handleWebSocketDisconnectListener(createDisconnectEvent("session-1"));

        clearInvocations(messagingTemplate);
        clearInvocations(userRepository);

        registry.handleWebSocketDisconnectListener(createDisconnectEvent("session-2"));

        assertThat(registry.isUserOnline(userId)).isFalse();
        assertThat(registry.getOnlineUserIds()).doesNotContain(userId);

        verify(userRepository).updateLastActiveAt(eq(userId), any(LocalDateTime.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), payloadCaptor.capture());

        Map<?, ?> payload = (Map<?, ?>) payloadCaptor.getValue();
        assertThat(payload.get("userId")).isEqualTo(userId);
        assertThat(payload.get("isOnline")).isEqualTo(false);
    }

    @Test
    @DisplayName("markLoggedOut: Cleans up user sessions immediately")
    void markLoggedOut_RemovesUserFromOnlineList() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        assertThat(registry.isUserOnline(userId)).isTrue();

        registry.markLoggedOut(userId);
        assertThat(registry.isUserOnline(userId)).isFalse();
    }

    @Test
    @DisplayName("DB exception during connect: Handled gracefully without throwing")
    void connect_DbExceptionHandledGracefully() {
        doThrow(new RuntimeException("DB Connection Failed"))
                .when(userRepository).updateLastActiveAt(eq(userId), any());

        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));

        // User is still tracked in-memory and broadcast still succeeds
        assertThat(registry.isUserOnline(userId)).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), any(Object.class));
    }
}
