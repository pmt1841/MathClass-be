package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class UserPresenceRegistry {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public UserPresenceRegistry(@Lazy SimpMessagingTemplate messagingTemplate, @Lazy UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    // Mapping userId -> Set các sessionId của người dùng đó (xử lý mở nhiều tab)
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    @Transactional
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                Long userId = userDetails.getId();
                if (sessionId != null) {
                    userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
                    log.info("User connected WebSocket STOMP: userId={}, sessionId={}, activeSessions={}",
                            userId, sessionId, userSessions.get(userId).size());

                    LocalDateTime now = LocalDateTime.now();
                    try {
                        userRepository.updateLastActiveAt(userId, now);
                    } catch (Exception ex) {
                        log.warn("Failed to update lastActiveAt on connect: {}", ex.getMessage());
                    }
                    broadcastPresence(userId, true, now);
                }
            }
        }
    }

    @Transactional
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                Long userId = userDetails.getId();
                if (sessionId != null && userSessions.containsKey(userId)) {
                    Set<String> sessions = userSessions.get(userId);
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        userSessions.remove(userId);
                        log.info("User completely disconnected all WebSocket sessions: userId={}", userId);
                        LocalDateTime now = LocalDateTime.now();
                        try {
                            userRepository.updateLastActiveAt(userId, now);
                        } catch (Exception ex) {
                            log.warn("Failed to update lastActiveAt on disconnect: {}", ex.getMessage());
                        }
                        broadcastPresence(userId, false, now);
                    } else {
                        log.info("User disconnected one session: userId={}, remainingSessions={}", userId, sessions.size());
                    }
                }
            }
        }
    }

    public void broadcastPresence(Long userId, boolean isOnline, LocalDateTime lastActiveAt) {
        if (userId != null && !isOnline) {
            userSessions.remove(userId);
        }
        if (messagingTemplate != null && userId != null) {
            try {
                Map<String, Object> payload = Map.of(
                        "userId", userId,
                        "isOnline", isOnline,
                        "lastActiveAt", lastActiveAt != null ? lastActiveAt.toString() : ""
                );
                messagingTemplate.convertAndSend("/topic/presence", (Object) payload);
            } catch (Exception ex) {
                log.warn("Failed to broadcast presence for user {}: {}", userId, ex.getMessage());
            }
        }
    }

    public void markLoggedOut(Long userId) {
        if (userId != null) {
            userSessions.remove(userId);
        }
    }

    public boolean isUserOnline(Long userId) {
        return userId != null && userSessions.containsKey(userId) && !userSessions.get(userId).isEmpty();
    }

    public Set<Long> getOnlineUserIds() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }
}
