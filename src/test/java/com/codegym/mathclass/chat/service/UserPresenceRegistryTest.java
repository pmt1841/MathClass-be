package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.chat.dto.event.PresenceMessage;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UserPresenceRegistryTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RSet<String> sessionsSet;

    @Mock
    private RSet<String> onlineUsersSet;

    @Mock
    private RBucket<String> sessionBucket;

    @Mock
    private RTopic presenceTopic;

    private UserPresenceRegistry registry;

    private final Long userId = 100L;
    private CustomUserDetails userDetails;
    private Set<String> backedSessions;
    private Set<String> backedOnlineUsers;

    @BeforeEach
    void setUp() {
        backedSessions = new HashSet<>();
        backedOnlineUsers = new HashSet<>();

        // Stubbing sessionsSet backed by backedSessions set
        lenient().when(sessionsSet.add(anyString())).thenAnswer(inv -> backedSessions.add(inv.getArgument(0)));
        lenient().when(sessionsSet.remove(anyString())).thenAnswer(inv -> backedSessions.remove(inv.getArgument(0)));
        lenient().when(sessionsSet.isEmpty()).thenAnswer(inv -> backedSessions.isEmpty());
        lenient().when(sessionsSet.size()).thenAnswer(inv -> backedSessions.size());

        // Stubbing onlineUsersSet backed by backedOnlineUsers set
        lenient().when(onlineUsersSet.add(anyString())).thenAnswer(inv -> backedOnlineUsers.add(inv.getArgument(0)));
        lenient().when(onlineUsersSet.remove(anyString())).thenAnswer(inv -> backedOnlineUsers.remove(inv.getArgument(0)));
        lenient().when(onlineUsersSet.contains(anyString())).thenAnswer(inv -> backedOnlineUsers.contains(inv.getArgument(0)));
        lenient().when(onlineUsersSet.readAll()).thenAnswer(inv -> new HashSet<>(backedOnlineUsers));

        // Stubbing RedissonClient
        lenient().doReturn(sessionsSet).when(redissonClient).getSet(startsWith(UserPresenceRegistry.USER_SESSIONS_PREFIX), any(StringCodec.class));
        lenient().doReturn(onlineUsersSet).when(redissonClient).getSet(eq(UserPresenceRegistry.ONLINE_USERS_KEY), any(StringCodec.class));
        lenient().doReturn(sessionBucket).when(redissonClient).getBucket(anyString(), any(StringCodec.class));
        lenient().when(redissonClient.getTopic(UserPresenceRegistry.PRESENCE_TOPIC_NAME)).thenReturn(presenceTopic);
        lenient().when(redissonClient.getTopic(eq(UserPresenceRegistry.PRESENCE_TOPIC_NAME), any())).thenReturn(presenceTopic);

        registry = new UserPresenceRegistry(messagingTemplate, userRepository, redissonClient);
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
    @DisplayName("Single tab: Connect -> isOnline is true, update DB, broadcast locally and publish to Redis topic")
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

        // Verify published to Redis presence topic
        ArgumentCaptor<PresenceMessage> msgCaptor = ArgumentCaptor.forClass(PresenceMessage.class);
        verify(presenceTopic).publish(msgCaptor.capture());
        PresenceMessage publishedMsg = msgCaptor.getValue();
        assertThat(publishedMsg.userId()).isEqualTo(userId);
        assertThat(publishedMsg.isOnline()).isTrue();
    }

    @Test
    @DisplayName("Multiple tabs: Connect tab 1, then tab 2 -> user stays online, only publishes online event once")
    void connectMultipleTabs_UserStaysOnline() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        registry.handleWebSocketConnectListener(createConnectEvent("session-2"));

        assertThat(registry.isUserOnline(userId)).isTrue();
        verify(userRepository, times(2)).updateLastActiveAt(eq(userId), any(LocalDateTime.class));

        // Only 1 online broadcast was triggered
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence"), any(Object.class));
        verify(presenceTopic, times(1)).publish(any(PresenceMessage.class));
    }

    @Test
    @DisplayName("Multiple tabs: Disconnect 1 of 2 tabs -> user stays online and does NOT broadcast offline")
    void disconnectOneOfMultipleTabs_UserStaysOnline() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        registry.handleWebSocketConnectListener(createConnectEvent("session-2"));

        clearInvocations(messagingTemplate);
        clearInvocations(userRepository);
        clearInvocations(presenceTopic);

        registry.handleWebSocketDisconnectListener(createDisconnectEvent("session-1"));

        // User is still online with session-2
        assertThat(registry.isUserOnline(userId)).isTrue();
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(userRepository, never()).updateLastActiveAt(any(), any());
        verify(presenceTopic, never()).publish(any(PresenceMessage.class));
    }

    @Test
    @DisplayName("Close all tabs: Disconnect last tab -> user goes offline, updates DB and broadcasts offline")
    void disconnectAllTabs_UserBecomesOffline() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        registry.handleWebSocketConnectListener(createConnectEvent("session-2"));

        registry.handleWebSocketDisconnectListener(createDisconnectEvent("session-1"));

        clearInvocations(messagingTemplate);
        clearInvocations(userRepository);
        clearInvocations(presenceTopic);

        registry.handleWebSocketDisconnectListener(createDisconnectEvent("session-2"));

        assertThat(registry.isUserOnline(userId)).isFalse();
        assertThat(registry.getOnlineUserIds()).doesNotContain(userId);

        verify(userRepository).updateLastActiveAt(eq(userId), any(LocalDateTime.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), payloadCaptor.capture());

        Map<?, ?> payload = (Map<?, ?>) payloadCaptor.getValue();
        assertThat(payload.get("userId")).isEqualTo(userId);
        assertThat(payload.get("isOnline")).isEqualTo(false);

        // Verify published offline to Redis presence topic
        ArgumentCaptor<PresenceMessage> msgCaptor = ArgumentCaptor.forClass(PresenceMessage.class);
        verify(presenceTopic).publish(msgCaptor.capture());
        PresenceMessage publishedMsg = msgCaptor.getValue();
        assertThat(publishedMsg.userId()).isEqualTo(userId);
        assertThat(publishedMsg.isOnline()).isFalse();
    }

    @Test
    @DisplayName("markLoggedOut: Cleans up user sessions immediately and publishes offline event")
    void markLoggedOut_RemovesUserFromOnlineList() {
        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));
        assertThat(registry.isUserOnline(userId)).isTrue();

        clearInvocations(messagingTemplate);
        clearInvocations(presenceTopic);

        registry.markLoggedOut(userId);
        assertThat(registry.isUserOnline(userId)).isFalse();

        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), any(Object.class));
        verify(presenceTopic).publish(any(PresenceMessage.class));
    }

    @Test
    @DisplayName("DB exception during connect: Handled gracefully without throwing")
    void connect_DbExceptionHandledGracefully() {
        doThrow(new RuntimeException("DB Connection Failed"))
                .when(userRepository).updateLastActiveAt(eq(userId), any());

        registry.handleWebSocketConnectListener(createConnectEvent("session-1"));

        // User is still tracked in Redis and broadcast still succeeds
        assertThat(registry.isUserOnline(userId)).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), any(Object.class));
        verify(presenceTopic).publish(any(PresenceMessage.class));
    }
}
